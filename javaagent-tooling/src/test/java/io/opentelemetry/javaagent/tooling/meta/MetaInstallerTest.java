/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetaInstallerTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void exportsAppStartedWithAllowlistedResource() throws Exception {
    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<byte[]> requestBody = new AtomicReference<>();
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    cleanup.deferCleanup(() -> server.stop(0));
    server.createContext(
        "/v1/meta",
        exchange -> {
          requestBody.set(readAll(exchange.getRequestBody()));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
          received.countDown();
        });
    server.start();

    Map<String, String> properties = new HashMap<>();
    properties.put("otel.meta.exporter", "meta");
    properties.put(
        "otel.exporter.meta.endpoint",
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/meta");
    properties.put("otel.exporter.meta.max-retries", "0");
    properties.put("otel.meta.batch.schedule-delay", "10");
    Resource resource =
        Resource.create(
            Attributes.builder()
                .put(stringKey("service.name"), "checkout")
                .put(stringKey("service.version"), "1.2.3")
                .put(stringKey("deployment.environment.name"), "production")
                .put(stringKey("host.name"), "checkout-host")
                .put(longKey("process.pid"), 1234)
                .put(stringKey("process.command_args"), "--secret=value")
                .build());

    MetaService service =
        requireNonNull(
            MetaInstaller.install(
                DefaultConfigProperties.createFromMap(properties), resource, "2.30.2"));
    cleanup.deferCleanup(service);

    assertThat(received.await(5, SECONDS)).isTrue();
    JsonNode root = new ObjectMapper().readTree(new String(requestBody.get(), UTF_8));
    assertThat(root.get("runtime_id").asText()).isEqualTo(service.getRuntimeId());
    assertThat(root.get("seq_id").asLong()).isEqualTo(1);
    assertThat(root.get("tracer_time").asLong()).isPositive();
    assertThat(root.has("application")).isFalse();
    assertThat(root.has("host")).isFalse();
    assertThat(root.get("resource").get("service.name").asText()).isEqualTo("checkout");
    assertThat(root.get("resource").get("service.version").asText()).isEqualTo("1.2.3");
    assertThat(root.get("resource").get("deployment.environment.name").asText())
        .isEqualTo("production");
    assertThat(root.get("resource").get("telemetry.distro.version").asText()).isEqualTo("2.30.2");
    assertThat(root.get("resource").get("telemetry.sdk.language").asText()).isEqualTo("java");
    assertThat(root.get("resource").get("host.name").asText()).isEqualTo("checkout-host");
    assertThat(root.get("resource").get("host.arch").asText()).isNotEmpty();
    assertThat(root.get("resource").get("process.runtime.name").asText()).isNotEmpty();
    assertThat(root.get("resource").get("process.runtime.version").asText()).isNotEmpty();
    assertThat(root.get("resource").get("process.pid").asLong()).isEqualTo(1234);
    assertThat(root.get("resource").has("process.command_args")).isFalse();
    JsonNode event = root.get("events").get(0);
    assertThat(event.has("sequence_id")).isFalse();
    assertThat(event.get("request_type").asText()).isEqualTo("app-started");
    assertThat(event.get("payload").has("agent_version")).isFalse();
    assertThat(event.get("payload").get("installation_method").asText()).isEqualTo("javaagent");
    assertThat(event.get("payload").get("startup_status").asText()).isEqualTo("success");
    JsonNode enabledCapabilities = event.get("payload").get("enabled_capabilities");
    assertThat(enabledCapabilities.size()).isEqualTo(5);
    assertThat(enabledCapabilities.get(0).asText()).isEqualTo("app-started");
    assertThat(enabledCapabilities.get(1).asText()).isEqualTo("app-dependencies-loaded");
    assertThat(enabledCapabilities.get(2).asText()).isEqualTo("app-integrations-change");
    assertThat(enabledCapabilities.get(3).asText()).isEqualTo("app-heartbeat");
    assertThat(enabledCapabilities.get(4).asText()).isEqualTo("app-extended-heartbeat");
  }

  private static byte[] readAll(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      outputStream.write(buffer, 0, read);
    }
    return outputStream.toByteArray();
  }
}
