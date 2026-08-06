/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.asyncprofiler.ProfileArtifact;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DatakitProfileExporterTest {

  @Test
  void uploadsMultipartPayload() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/profiling/v1/input",
        exchange -> {
          requestBody.set(readAll(exchange.getRequestBody()));
          respond(exchange);
        });
    server.start();

    Path profileFile = Files.createTempFile("otel-async-profiler-datakit", ".jfr");
    Files.write(profileFile, "fake-jfr-data".getBytes(UTF_8));

    try {
      DatakitProfileExporter exporter =
          new DatakitProfileExporter(
              new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/profiling/v1/input"),
              Duration.ofSeconds(5),
              singletonMap("service", "test-service"));
      exporter.export(
          new ProfileArtifact(
              profileFile,
              Instant.parse("2026-01-01T00:00:00Z"),
              Instant.parse("2026-01-01T00:00:05Z"),
              "jfr"));
    } finally {
      server.stop(0);
    }

    String body = requestBody.get();
    assertTrue(body.contains("name=\"main\"; filename=\"main.jfr\""));
    assertTrue(body.contains("name=\"event\"; filename=\"event.json\""));
    assertTrue(body.contains("\"format\":\"jfr\""));
    assertTrue(body.contains("\"tags_profiler\":\"service:test-service\""));
    assertTrue(body.contains("fake-jfr-data"));
  }

  private static void respond(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(200, 0);
    exchange.getResponseBody().close();
    exchange.close();
  }

  private static String readAll(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        outputStream.write(buffer, 0, read);
      }
      return outputStream.toString(UTF_8.name());
    } finally {
      inputStream.close();
      outputStream.close();
    }
  }
}
