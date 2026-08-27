/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.javaagent.tooling.EmptyConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Initializes Meta event collectors before application instrumentations are installed. */
@AutoService(BeforeAgentListener.class)
public class MetaInitializer implements BeforeAgentListener {

  private static final Logger logger = Logger.getLogger(MetaInitializer.class.getName());

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    if (config == null) {
      config = EmptyConfigProperties.INSTANCE;
    }
    initialize(config, InstrumentationHolder.getInstrumentation());
  }

  static void initialize(ConfigProperties config, @Nullable Instrumentation instrumentation) {
    try {
      if (instrumentation == null) {
        logger.fine("Meta event collectors are disabled because Instrumentation is unavailable");
        MetaTelemetry.disable();
        return;
      }
      MetaTelemetry.initialize(MetaConfiguration.create(config), instrumentation);
    } catch (IllegalArgumentException e) {
      logger.log(FINE, "Meta event collectors are disabled: " + e.getMessage());
      MetaTelemetry.disable();
    } catch (RuntimeException | LinkageError e) {
      logger.log(
          WARNING,
          "Meta event collectors failed to initialize and are disabled: {0}",
          e.getClass().getName());
      logger.log(FINE, "Meta event collector initialization failure", e);
      MetaTelemetry.disable();
    }
  }
}
