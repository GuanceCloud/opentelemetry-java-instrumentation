/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.asyncprofiler.AsyncProfilerConfig;
import io.opentelemetry.instrumentation.asyncprofiler.AsyncProfilerRuntime;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Starts async-profiler during javaagent startup. */
@AutoService(AgentListener.class)
public class AsyncProfilerInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(AsyncProfilerInstaller.class.getName());
  private static final AtomicBoolean installed = new AtomicBoolean();

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    if (!installed.compareAndSet(false, true)) {
      return;
    }

    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    if (config == null || !AgentAsyncProfilerConfiguration.isEnabled(config)) {
      return;
    }

    try {
      AsyncProfilerConfig asyncProfilerConfig =
          AgentAsyncProfilerConfiguration.create(
              config, SdkAutoconfigureAccess.getResource(autoConfiguredSdk));
      AsyncProfilerRuntime runtime = new AsyncProfilerRuntime(asyncProfilerConfig);
      runtime.start();
      Runtime.getRuntime().addShutdownHook(new AsyncProfilerShutdownHook(runtime));
    } catch (RuntimeException exception) {
      logger.log(WARNING, "Unable to initialize async-profiler integration", exception);
    }
  }
}
