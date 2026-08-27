/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.FINE;

import java.lang.instrument.Instrumentation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Coordinates Meta collectors that run before and after Java agent installation. */
public final class MetaTelemetry {

  private static final Logger logger = Logger.getLogger(MetaTelemetry.class.getName());
  private static final Object lock = new Object();

  @Nullable private static volatile MetaDependencyCollector dependencyCollector;
  @Nullable private static volatile MetaIntegrationCollector integrationCollector;
  @Nullable private static volatile Instrumentation instrumentation;
  private static final PendingIntegrations pendingIntegrations = new PendingIntegrations();
  private static boolean initialized;
  private static volatile boolean disabled;

  static void initialize(MetaConfiguration configuration, Instrumentation instrumentation) {
    synchronized (lock) {
      if (initialized) {
        return;
      }
      initialized = true;
      if (!configuration.isEnabled()) {
        return;
      }

      MetaDependencyCollector dependencies = null;
      boolean dependencyTransformerAdded = false;
      try {
        if (configuration.isAppDependenciesLoadedEnabled()) {
          dependencies = new MetaDependencyCollector();
          instrumentation.addTransformer(dependencies);
          dependencyTransformerAdded = true;
        }
        MetaIntegrationCollector integrations =
            configuration.isAppIntegrationsChangeEnabled() ? new MetaIntegrationCollector() : null;

        dependencyCollector = dependencies;
        integrationCollector = integrations;
        MetaTelemetry.instrumentation = dependencyTransformerAdded ? instrumentation : null;
        disabled = false;
      } catch (RuntimeException | LinkageError e) {
        if (dependencyTransformerAdded && dependencies != null) {
          try {
            instrumentation.removeTransformer(dependencies);
          } catch (RuntimeException | LinkageError cleanupError) {
            e.addSuppressed(cleanupError);
          }
        }
        throw e;
      }
    }
  }

  static void disable() {
    MetaDependencyCollector dependencies;
    MetaIntegrationCollector integrations;
    Instrumentation currentInstrumentation;
    synchronized (lock) {
      initialized = true;
      disabled = true;
      dependencies = dependencyCollector;
      integrations = integrationCollector;
      currentInstrumentation = instrumentation;
      dependencyCollector = null;
      integrationCollector = null;
      instrumentation = null;
    }
    if (dependencies != null && currentInstrumentation != null) {
      try {
        currentInstrumentation.removeTransformer(dependencies);
      } catch (RuntimeException | LinkageError e) {
        logger.log(FINE, "Unable to remove the Meta dependency transformer", e);
      }
    }
    if (dependencies != null) {
      closeCollector(dependencies, "dependency");
    }
    if (integrations != null) {
      closeCollector(integrations, "integration");
    }
  }

  static boolean isDisabled() {
    return disabled;
  }

  static void resetForTest() {
    disable();
    synchronized (lock) {
      initialized = false;
      disabled = false;
    }
  }

  /** Returns whether successful instrumentation transformations should be collected. */
  public static boolean isIntegrationCollectionEnabled() {
    return integrationCollector != null;
  }

  /** Records an instrumentation whose type matcher and transformation chain were selected. */
  public static void integrationMatched(String typeName, String instrumentationName) {
    if (integrationCollector == null
        || typeName == null
        || typeName.isEmpty()
        || instrumentationName == null
        || instrumentationName.isEmpty()) {
      return;
    }
    pendingIntegrations.add(typeName, instrumentationName);
  }

  /** Commits integrations only after Byte Buddy reports a successful class transformation. */
  public static void integrationTransformationSucceeded(String typeName) {
    Set<String> names = pendingIntegrations.remove(typeName);
    MetaIntegrationCollector collector = integrationCollector;
    if (collector != null && names != null && !names.isEmpty()) {
      collector.record(names);
    }
  }

  /** Clears instrumentation matches when transformation fails or completes without a transform. */
  public static void integrationTransformationFinished(String typeName) {
    pendingIntegrations.remove(typeName);
  }

  static void start(MetaService service) {
    MetaDependencyCollector dependencies = dependencyCollector;
    if (dependencies != null) {
      dependencies.start(service);
    }
    MetaIntegrationCollector integrations = integrationCollector;
    if (integrations != null) {
      integrations.start(service);
    }
  }

  static Map<String, Object> extendedHeartbeatPayload() {
    MetaDependencyCollector dependencies = dependencyCollector;
    MetaIntegrationCollector integrations = integrationCollector;
    Map<String, Object> payload = new LinkedHashMap<>();
    // Reserved for allowlisted agent configuration telemetry. Never expose raw configuration,
    // because exporter headers and other settings can contain credentials.
    payload.put("configuration", emptyList());
    payload.put("dependencies", dependencies == null ? emptyList() : dependencies.snapshot());
    payload.put("integrations", integrations == null ? emptyList() : integrations.snapshot());
    return payload;
  }

  static void shutdown(MetaService service) {
    MetaDependencyCollector dependencies;
    MetaIntegrationCollector integrations;
    Instrumentation currentInstrumentation;
    synchronized (lock) {
      dependencies = dependencyCollector;
      integrations = integrationCollector;
      currentInstrumentation = instrumentation;
    }

    // Keep the collectors visible to extended heartbeats until collection and export have stopped.
    if (dependencies != null && currentInstrumentation != null) {
      try {
        currentInstrumentation.removeTransformer(dependencies);
      } catch (RuntimeException | LinkageError e) {
        logger.log(FINE, "Unable to remove the Meta dependency transformer", e);
      }
    }
    if (dependencies != null) {
      closeCollector(dependencies, "dependency");
    }
    if (integrations != null) {
      closeCollector(integrations, "integration");
    }
    try {
      service.close();
    } catch (RuntimeException | LinkageError e) {
      logger.log(FINE, "Unable to stop the Meta service", e);
    } finally {
      synchronized (lock) {
        if (dependencyCollector == dependencies) {
          dependencyCollector = null;
        }
        if (integrationCollector == integrations) {
          integrationCollector = null;
        }
        if (instrumentation == currentInstrumentation) {
          instrumentation = null;
        }
      }
    }
  }

  private static void closeCollector(AutoCloseable collector, String collectorName) {
    try {
      collector.close();
    } catch (Exception | LinkageError e) {
      logger.log(FINE, "Unable to stop the Meta " + collectorName + " collector", e);
    }
  }

  static final class PendingIntegrations {
    private static final ThreadLocal<Map<String, Set<String>>> pending = new ThreadLocal<>();

    void add(String typeName, String instrumentationName) {
      Map<String, Set<String>> integrationsByType = pending.get();
      if (integrationsByType == null) {
        integrationsByType = new LinkedHashMap<>();
        pending.set(integrationsByType);
      }
      integrationsByType
          .computeIfAbsent(typeName, unused -> new LinkedHashSet<>())
          .add(instrumentationName);
    }

    @Nullable
    Set<String> remove(String typeName) {
      Map<String, Set<String>> integrationsByType = pending.get();
      if (integrationsByType == null) {
        return null;
      }
      Set<String> result = integrationsByType.remove(typeName);
      if (integrationsByType.isEmpty()) {
        pending.remove();
      }
      return result;
    }
  }

  private MetaTelemetry() {}
}
