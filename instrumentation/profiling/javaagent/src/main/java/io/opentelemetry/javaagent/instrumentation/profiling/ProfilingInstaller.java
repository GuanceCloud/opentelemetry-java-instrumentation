/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.profiling;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.profiling.ProfileExporterAdapter;
import io.opentelemetry.instrumentation.profiling.RuntimeProfiling;
import io.opentelemetry.instrumentation.profiling.internal.DatakitProfileExporterAdapter;
import io.opentelemetry.instrumentation.profiling.internal.FileProfileExporterAdapter;
import io.opentelemetry.instrumentation.profiling.internal.JfrSupport;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Starts the experimental JVM profiling subsystem during javaagent startup. */
@AutoService(AgentListener.class)
public class ProfilingInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(ProfilingInstaller.class.getName());
  private static final AtomicBoolean installed = new AtomicBoolean();
  private static final ProfileExporterAdapter noopExporter = snapshot -> {};

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    if (!installed.compareAndSet(false, true)) {
      return;
    }

    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    if (config == null || !AgentProfilingConfiguration.isEnabled(config)) {
      return;
    }
    if (!JfrSupport.isJfrAvailable()) {
      logger.warning(
          "Profiling is enabled but JFR is unavailable. Profiling currently requires Java 11+ with JFR available.");
      return;
    }

    AgentProfilingConfiguration configuration = AgentProfilingConfiguration.create(config);
    if ("none".equals(configuration.getExporter())) {
      logger.warning(
          "Profiling is enabled but exporter is set to 'none'. Profiling snapshots will be discarded.");
    }
    ProfileExporterAdapter exporterAdapter =
        createExporter(configuration, SdkAutoconfigureAccess.getResource(autoConfiguredSdk));
    RuntimeProfiling profiling =
        RuntimeProfiling.builder()
            .interval(configuration.getInterval())
            .startupDelay(configuration.getStartupDelay())
            .maxAge(configuration.getMaxAge())
            .maxSize(configuration.getMaxSize())
            .stackDepth(configuration.getStackDepth())
            .tempDir(configuration.getTempDir())
            .memoryEnabled(configuration.isMemoryEnabled())
            .memoryAllocationSampling(configuration.getMemoryAllocationSampling())
            .memoryOldObjectSampling(configuration.getMemoryOldObjectSampling())
            .exporter(exporterAdapter)
            .build();
    profiling.start();

    try {
      Runtime.getRuntime().addShutdownHook(new ProfilingShutdownHook(profiling));
    } catch (IllegalStateException ignored) {
      profiling.close();
    }
  }

  private static ProfileExporterAdapter createExporter(
      AgentProfilingConfiguration configuration, Resource resource) {
    String exporterName = configuration.getExporter();
    if ("file".equalsIgnoreCase(exporterName)) {
      return new FileProfileExporterAdapter(configuration.getFileExportPath());
    }
    if ("datakit".equalsIgnoreCase(exporterName)) {
      return new DatakitProfileExporterAdapter(
          configuration.getDatakitEndpoint(),
          configuration.getDatakitTimeout(),
          DatakitProfilingTags.create(resource));
    }
    if (!"none".equalsIgnoreCase(exporterName)) {
      logger.warning("Unknown profiling exporter '" + exporterName + "', falling back to none.");
    }
    return noopExporter;
  }
}
