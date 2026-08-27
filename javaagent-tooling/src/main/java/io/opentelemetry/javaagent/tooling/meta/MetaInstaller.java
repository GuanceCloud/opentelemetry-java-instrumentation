/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.tooling.AgentVersion;
import io.opentelemetry.javaagent.tooling.EmptyConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Starts the dedicated meta exporter after Java agent installation. */
@AutoService(AgentListener.class)
public class MetaInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(MetaInstaller.class.getName());
  private static final AtomicBoolean installed = new AtomicBoolean();

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    if (!installed.compareAndSet(false, true)) {
      return;
    }

    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    if (config == null) {
      config = EmptyConfigProperties.INSTANCE;
    }
    install(config, SdkAutoconfigureAccess.getResource(autoConfiguredSdk), AgentVersion.VERSION);
  }

  @Nullable
  static MetaService install(
      ConfigProperties config, Resource resource, @Nullable String agentVersion) {
    MetaConfiguration configuration;
    try {
      configuration = MetaConfiguration.create(config);
    } catch (IllegalArgumentException e) {
      logger.log(WARNING, "Meta exporter is disabled: " + e.getMessage());
      return null;
    }
    if (!configuration.isEnabled()) {
      return null;
    }
    if (MetaTelemetry.isDisabled()) {
      logger.warning("Meta exporter is disabled because its event collectors failed to initialize");
      return null;
    }

    MetaService service = null;
    try {
      Map<String, Object> metaResource = MetaResource.from(resource, agentVersion);
      service = new MetaService(new HttpMetaExporter(configuration), configuration, metaResource);
      service.start();
      if (configuration.isAppStartedEnabled()) {
        service.emit("app-started", appStartedPayload(configuration));
      }
      MetaTelemetry.start(service);
      addShutdownHook(service);
      return service;
    } catch (RuntimeException | LinkageError e) {
      try {
        if (service == null) {
          MetaTelemetry.disable();
        } else {
          MetaTelemetry.shutdown(service);
        }
      } catch (RuntimeException | LinkageError cleanupError) {
        e.addSuppressed(cleanupError);
      }
      logger.log(WARNING, "Meta exporter failed to start and is disabled", e);
      return null;
    }
  }

  private static Map<String, Object> appStartedPayload(MetaConfiguration configuration) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("installation_method", "javaagent");
    payload.put("startup_status", "success");
    List<String> enabledCapabilities = new ArrayList<>();
    if (configuration.isAppStartedEnabled()) {
      enabledCapabilities.add("app-started");
    }
    if (configuration.isAppDependenciesLoadedEnabled()) {
      enabledCapabilities.add("app-dependencies-loaded");
    }
    if (configuration.isAppIntegrationsChangeEnabled()) {
      enabledCapabilities.add("app-integrations-change");
    }
    enabledCapabilities.add("app-heartbeat");
    enabledCapabilities.add("app-extended-heartbeat");
    payload.put("enabled_capabilities", enabledCapabilities);
    return payload;
  }

  private static void addShutdownHook(MetaService service) {
    Thread shutdownHook =
        new Thread(
            () -> {
              MetaTelemetry.shutdown(service);
            },
            "opentelemetry-meta-shutdown");
    shutdownHook.setContextClassLoader(null);
    try {
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    } catch (IllegalStateException ignored) {
      MetaTelemetry.shutdown(service);
    } catch (SecurityException e) {
      logger.log(FINE, "Unable to register meta exporter shutdown hook", e);
    }
  }
}
