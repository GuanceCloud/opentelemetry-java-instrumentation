/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.tooling.ExtensionClassLoader;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.InstrumentationModuleClassLoader;
import java.lang.instrument.ClassFileTransformer;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class MetaDependencyCollector implements ClassFileTransformer, AutoCloseable {

  private static final Logger logger = Logger.getLogger(MetaDependencyCollector.class.getName());
  private static final int MAX_LOCATIONS = 1_024;
  private static final int MAX_DEPENDENCIES = 1_024;
  private static final int MAX_RESOLVE_BATCH_SIZE = 32;
  private static final long POLL_DELAY_MILLIS = 200;

  private final BlockingQueue<URL> locations;
  private final Set<String> seenLocations = ConcurrentHashMap.newKeySet();
  // Only accessed by the resolver worker. Retaining payloads lets every event carry a full
  // snapshot, so receivers can replace state instead of merging increments.
  private final Map<String, Map<String, Object>> dependencies = new LinkedHashMap<>();
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicBoolean limitWarningLogged = new AtomicBoolean();
  private final AtomicBoolean dependencyLimitWarningLogged = new AtomicBoolean();
  private final Thread worker = new Thread(this::runWorker, "opentelemetry-meta-dependencies");
  private final int maxLocations;
  private final int maxDependencies;
  @Nullable private final String agentLocation;

  private volatile List<Map<String, Object>> currentDependencies = emptyList();

  @Nullable private volatile MetaService service;

  MetaDependencyCollector() {
    this(codeSourceLocation(MetaDependencyCollector.class), MAX_LOCATIONS, MAX_DEPENDENCIES);
  }

  MetaDependencyCollector(@Nullable URL agentLocation) {
    this(agentLocation, MAX_LOCATIONS, MAX_DEPENDENCIES);
  }

  MetaDependencyCollector(@Nullable URL agentLocation, int maxLocations, int maxDependencies) {
    this.locations = new ArrayBlockingQueue<>(maxLocations);
    this.maxLocations = maxLocations;
    this.maxDependencies = maxDependencies;
    this.agentLocation = agentLocation == null ? null : enclosingJar(agentLocation);
    worker.setDaemon(true);
    worker.setContextClassLoader(null);
  }

  @Override
  @Nullable
  public byte[] transform(
      ClassLoader loader,
      String className,
      @Nullable Class<?> classBeingRedefined,
      @Nullable ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {
    if (shouldIgnore(loader, className) || protectionDomain == null) {
      return null;
    }
    CodeSource codeSource = protectionDomain.getCodeSource();
    if (codeSource != null) {
      recordLocation(codeSource.getLocation());
    }
    return null;
  }

  private static boolean shouldIgnore(ClassLoader loader, String className) {
    return loader == null
        || loader instanceof AgentClassLoader
        || loader instanceof ExtensionClassLoader
        || loader instanceof InstrumentationModuleClassLoader
        || className == null
        || className.startsWith("io/opentelemetry/javaagent/");
  }

  void recordLocation(@Nullable URL location) {
    if (location == null) {
      return;
    }
    String key = location.toExternalForm();
    if (enclosingJar(location).equals(agentLocation)) {
      return;
    }
    if (seenLocations.contains(key)) {
      return;
    }
    if (seenLocations.size() >= maxLocations) {
      if (limitWarningLogged.compareAndSet(false, true)) {
        logger.log(
            WARNING,
            "Meta dependency location limit of {0} was reached; additional locations are ignored",
            maxLocations);
      }
    } else if (seenLocations.add(key) && !locations.offer(location)) {
      seenLocations.remove(key);
      if (limitWarningLogged.compareAndSet(false, true)) {
        logger.warning("Meta dependency location queue is full; a location was dropped");
      }
    }
  }

  @Nullable
  private static URL codeSourceLocation(Class<?> type) {
    ProtectionDomain protectionDomain = type.getProtectionDomain();
    if (protectionDomain == null) {
      return null;
    }
    CodeSource codeSource = protectionDomain.getCodeSource();
    return codeSource == null ? null : codeSource.getLocation();
  }

  private static String enclosingJar(URL location) {
    String value = location.toExternalForm();
    if (value.startsWith("jar:")) {
      value = value.substring("jar:".length());
    }
    int nestedSeparator = value.indexOf("!/");
    return nestedSeparator < 0 ? value : value.substring(0, nestedSeparator);
  }

  void start(MetaService service) {
    this.service = service;
    if (running.compareAndSet(false, true)) {
      worker.start();
    }
  }

  private void runWorker() {
    while (running.get() || !locations.isEmpty()) {
      URL first;
      try {
        first = locations.poll(POLL_DELAY_MILLIS, MILLISECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return;
      }
      if (first == null) {
        continue;
      }

      List<URL> batch = new ArrayList<>(MAX_RESOLVE_BATCH_SIZE);
      batch.add(first);
      locations.drainTo(batch, MAX_RESOLVE_BATCH_SIZE - 1);
      boolean changed = false;
      for (URL location : batch) {
        try {
          for (MetaDependency dependency : MetaDependencyResolver.resolve(location)) {
            if (dependencies.containsKey(dependency.key())) {
              continue;
            }
            if (dependencies.size() >= maxDependencies) {
              if (dependencyLimitWarningLogged.compareAndSet(false, true)) {
                logger.log(
                    WARNING,
                    "Meta dependency limit of {0} was reached; additional dependencies are ignored",
                    maxDependencies);
              }
              break;
            } else {
              dependencies.put(dependency.key(), dependency.toPayload());
              changed = true;
            }
          }
        } catch (RuntimeException e) {
          logger.log(FINE, "Unable to collect Meta dependency from " + location, e);
        }
      }
      if (!changed) {
        continue;
      }
      List<Map<String, Object>> snapshot =
          Collections.unmodifiableList(new ArrayList<>(dependencies.values()));
      currentDependencies = snapshot;
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("dependencies", snapshot);
      MetaService currentService = service;
      if (currentService != null) {
        currentService.emit("app-dependencies-loaded", payload);
      }
    }
  }

  List<Map<String, Object>> snapshot() {
    return currentDependencies;
  }

  @Override
  @SuppressWarnings("Interruption") // Cancels this collector's owned daemon after graceful timeout.
  public void close() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    try {
      worker.join(5_000);
      if (worker.isAlive()) {
        worker.interrupt();
        logger.warning("Timed out waiting for the Meta dependency collector to stop");
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }
}
