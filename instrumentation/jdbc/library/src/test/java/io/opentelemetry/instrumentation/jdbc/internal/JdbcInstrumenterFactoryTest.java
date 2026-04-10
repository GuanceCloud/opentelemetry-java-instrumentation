/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

@SuppressWarnings("deprecation") // using deprecated config property
class JdbcInstrumenterFactoryTest {

  @Test
  void captureQueryParametersDisabledByDefault() {
    assertThat(JdbcInstrumenterFactory.captureQueryParameters(OpenTelemetry.noop())).isFalse();
  }

  @SetSystemProperty(
      key = "otel.instrumentation.jdbc.experimental.capture-query-parameters",
      value = "true")
  @Test
  void captureQueryParametersEnabledByExperimentalProperty() {
    assertThat(JdbcInstrumenterFactory.captureQueryParameters(OpenTelemetry.noop())).isTrue();
  }

  @SetSystemProperty(key = "otel.jdbc.sql.obfuscation", value = "true")
  @Test
  void captureQueryParametersEnabledByCompatibilityProperty() {
    assertThat(JdbcInstrumenterFactory.captureQueryParameters(OpenTelemetry.noop())).isTrue();
  }

  @SetSystemProperty(
      key = "otel.instrumentation.jdbc.experimental.capture-query-parameters",
      value = "false")
  @SetSystemProperty(key = "otel.jdbc.sql.obfuscation", value = "true")
  @Test
  void captureQueryParametersEnabledWhenEitherPropertyIsTrue() {
    assertThat(JdbcInstrumenterFactory.captureQueryParameters(OpenTelemetry.noop())).isTrue();
  }
}
