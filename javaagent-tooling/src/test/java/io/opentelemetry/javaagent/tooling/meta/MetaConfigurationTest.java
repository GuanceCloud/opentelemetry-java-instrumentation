/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetaConfigurationTest {

  @Test
  void disabledByDefault() {
    MetaConfiguration configuration =
        MetaConfiguration.create(DefaultConfigProperties.createFromMap(emptyMap()));

    assertThat(configuration.isEnabled()).isFalse();
  }

  @Test
  void readsExporterConfiguration() {
    Map<String, String> properties = validProperties();
    properties.put("otel.exporter.meta.headers", "Authorization=Bearer%20token,x-test=value");
    properties.put("otel.exporter.meta.timeout", "2500");
    properties.put("otel.exporter.meta.compression", "gzip");
    properties.put("otel.exporter.meta.max-retries", "4");
    properties.put("otel.exporter.meta.max-request-size", "1048576");
    properties.put("otel.meta.batch.schedule-delay", "25");
    properties.put("otel.meta.shutdown-timeout", "5000");
    properties.put("otel.meta.heartbeat.interval", "6000");
    properties.put("otel.meta.extended-heartbeat.interval", "12000");
    properties.put("otel.meta.app-started.enabled", "false");
    properties.put("otel.meta.app-dependencies-loaded.enabled", "false");
    properties.put("otel.meta.app-integrations-change.enabled", "false");

    MetaConfiguration configuration =
        MetaConfiguration.create(DefaultConfigProperties.createFromMap(properties));

    assertThat(configuration.isEnabled()).isTrue();
    assertThat(configuration.getEndpoint().toString()).isEqualTo("http://localhost:8080/v1/meta");
    assertThat(configuration.getHeaders())
        .containsEntry("Authorization", "Bearer token")
        .containsEntry("x-test", "value");
    assertThat(configuration.getTimeoutMillis()).isEqualTo(2500);
    assertThat(configuration.isGzipEnabled()).isTrue();
    assertThat(configuration.getMaxRetries()).isEqualTo(4);
    assertThat(configuration.getMaxRequestSizeBytes()).isEqualTo(1_048_576);
    assertThat(configuration.getScheduleDelayMillis()).isEqualTo(25);
    assertThat(configuration.getShutdownTimeoutMillis()).isEqualTo(5000);
    assertThat(configuration.getHeartbeatIntervalMillis()).isEqualTo(6000);
    assertThat(configuration.getExtendedHeartbeatIntervalMillis()).isEqualTo(12000);
    assertThat(configuration.isAppStartedEnabled()).isFalse();
    assertThat(configuration.isAppDependenciesLoadedEnabled()).isFalse();
    assertThat(configuration.isAppIntegrationsChangeEnabled()).isFalse();
  }

  @Test
  void supportsLegacyBatchExportTimeoutAsShutdownTimeoutAlias() {
    Map<String, String> properties = validProperties();
    properties.put("otel.meta.batch.export-timeout", "4321");

    MetaConfiguration configuration =
        MetaConfiguration.create(DefaultConfigProperties.createFromMap(properties));

    assertThat(configuration.getShutdownTimeoutMillis()).isEqualTo(4321);
  }

  @Test
  void enablesAllEventTypesByDefaultWhenMetaExporterIsSelected() {
    MetaConfiguration configuration =
        MetaConfiguration.create(DefaultConfigProperties.createFromMap(validProperties()));

    assertThat(configuration.isAppStartedEnabled()).isTrue();
    assertThat(configuration.isAppDependenciesLoadedEnabled()).isTrue();
    assertThat(configuration.isAppIntegrationsChangeEnabled()).isTrue();
    assertThat(configuration.getHeartbeatIntervalMillis()).isEqualTo(60_000);
    assertThat(configuration.getExtendedHeartbeatIntervalMillis()).isEqualTo(86_400_000);
  }

  @Test
  void requiresEndpointWhenMetaExporterIsSelected() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.meta.exporter", "meta");

    assertThatThrownBy(
            () -> MetaConfiguration.create(DefaultConfigProperties.createFromMap(properties)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("otel.exporter.meta.endpoint");
  }

  @Test
  void rejectsInvalidConfiguration() {
    Map<String, String> invalidShutdownProperties = validProperties();
    invalidShutdownProperties.put("otel.meta.shutdown-timeout", "0");

    assertThatThrownBy(
            () ->
                MetaConfiguration.create(
                    DefaultConfigProperties.createFromMap(invalidShutdownProperties)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("otel.meta.shutdown-timeout");

    Map<String, String> invalidEndpointProperties = validProperties();
    invalidEndpointProperties.put("otel.exporter.meta.endpoint", "ftp://localhost/v1/meta");
    assertThatThrownBy(
            () ->
                MetaConfiguration.create(
                    DefaultConfigProperties.createFromMap(invalidEndpointProperties)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("http or https");

    Map<String, String> invalidHeartbeatProperties = validProperties();
    invalidHeartbeatProperties.put("otel.meta.heartbeat.interval", "0");
    assertThatThrownBy(
            () ->
                MetaConfiguration.create(
                    DefaultConfigProperties.createFromMap(invalidHeartbeatProperties)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("otel.meta.heartbeat.interval");
  }

  @Test
  void rejectsInvalidOrReservedHeaders() {
    Map<String, String> reservedHeaderProperties = validProperties();
    reservedHeaderProperties.put("otel.exporter.meta.headers", "Content-Encoding=gzip");
    assertThatThrownBy(
            () ->
                MetaConfiguration.create(
                    DefaultConfigProperties.createFromMap(reservedHeaderProperties)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved header");

    Map<String, String> invalidHeaderProperties = validProperties();
    invalidHeaderProperties.put("otel.exporter.meta.headers", "x-test=first%0Asecond");
    assertThatThrownBy(
            () ->
                MetaConfiguration.create(
                    DefaultConfigProperties.createFromMap(invalidHeaderProperties)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid HTTP header value");
  }

  private static Map<String, String> validProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.meta.exporter", "meta");
    properties.put("otel.exporter.meta.endpoint", "http://localhost:8080/v1/meta");
    return properties;
  }
}
