/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

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

final class MetaIntegrationCollector implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(MetaIntegrationCollector.class.getName());
  private static final int MAX_INTEGRATIONS = 1_024;
  private static final int MAX_INTEGRATION_NAME_LENGTH = 256;
  private static final long POLL_DELAY_MILLIS = 200;

  private final BlockingQueue<String> pending = new ArrayBlockingQueue<>(MAX_INTEGRATIONS);
  private final Set<String> seen = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicBoolean limitWarningLogged = new AtomicBoolean();
  private final AtomicBoolean invalidNameWarningLogged = new AtomicBoolean();
  private final Thread worker = new Thread(this::runWorker, "opentelemetry-meta-integrations");

  private volatile List<Map<String, Object>> currentIntegrations = emptyList();

  @Nullable private volatile MetaService service;

  MetaIntegrationCollector() {
    worker.setDaemon(true);
    worker.setContextClassLoader(null);
  }

  void record(Iterable<String> instrumentationNames) {
    for (String name : instrumentationNames) {
      if (name == null || name.isEmpty() || seen.contains(name)) {
        continue;
      }
      if (name.length() > MAX_INTEGRATION_NAME_LENGTH) {
        if (invalidNameWarningLogged.compareAndSet(false, true)) {
          logger.log(
              WARNING,
              "Meta integration names longer than {0} characters are ignored",
              MAX_INTEGRATION_NAME_LENGTH);
        }
        continue;
      }
      if (seen.size() >= MAX_INTEGRATIONS) {
        if (limitWarningLogged.compareAndSet(false, true)) {
          logger.log(
              WARNING,
              "Meta integration limit of {0} was reached; additional integrations are ignored",
              MAX_INTEGRATIONS);
        }
      } else if (seen.add(name) && !pending.offer(name)) {
        seen.remove(name);
        if (limitWarningLogged.compareAndSet(false, true)) {
          logger.warning("Meta integration queue is full; an integration update was dropped");
        }
      }
    }
  }

  void start(MetaService service) {
    this.service = service;
    if (running.compareAndSet(false, true)) {
      worker.start();
    }
  }

  private void runWorker() {
    while (running.get() || !pending.isEmpty()) {
      String first;
      try {
        first = pending.poll(POLL_DELAY_MILLIS, MILLISECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return;
      }
      if (first == null) {
        continue;
      }

      List<String> observedChanges = new ArrayList<>();
      observedChanges.add(first);
      pending.drainTo(observedChanges);
      List<String> names = new ArrayList<>(seen);
      Collections.sort(names);
      List<Map<String, Object>> integrations = new ArrayList<>(names.size());
      for (String name : names) {
        Map<String, Object> integration = new LinkedHashMap<>();
        integration.put("name", name);
        integration.put("enabled", true);
        integrations.add(integration);
      }
      integrations = Collections.unmodifiableList(integrations);
      currentIntegrations = integrations;
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("integrations", integrations);
      MetaService currentService = service;
      if (currentService != null) {
        currentService.emit("app-integrations-change", payload);
      }
    }
  }

  List<Map<String, Object>> snapshot() {
    return currentIntegrations;
  }

  @Override
  @SuppressWarnings("Interruption") // Cancels this collector's owned daemon after graceful timeout.
  public void close() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    try {
      worker.join(1_000);
      if (worker.isAlive()) {
        worker.interrupt();
        logger.warning("Timed out waiting for the Meta integration collector to stop");
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }
}
