/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

class BinaryHttpProfileExporterTest {

  @Test
  void uploadsRawProfilePayload() throws Exception {
    AtomicReference<String> requestHeader = new AtomicReference<>();
    AtomicReference<byte[]> requestBody = new AtomicReference<>();
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/profiles",
        exchange -> {
          requestHeader.set(exchange.getRequestHeaders().getFirst("X-Test-Header"));
          requestBody.set(readAllBytes(exchange.getRequestBody()));
          respond(exchange);
        });
    server.start();

    Path payload = Files.createTempFile("otel-async-profiler-otlp", ".bin");
    Files.write(payload, "profile-payload".getBytes(UTF_8));

    try {
      BinaryHttpProfileExporter exporter =
          new BinaryHttpProfileExporter(
              new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/profiles"),
              Duration.ofSeconds(5),
              "application/x-protobuf",
              singletonMap("X-Test-Header", "present"));
      exporter.export(
          new ProfileArtifact(
              payload,
              Instant.parse("2026-01-01T00:00:00Z"),
              Instant.parse("2026-01-01T00:00:05Z"),
              "otlp"));
    } finally {
      server.stop(0);
    }

    assertEquals("present", requestHeader.get());
    assertTrue(new String(requestBody.get(), UTF_8).contains("profile-payload"));
  }

  private static void respond(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(200, 0);
    exchange.getResponseBody().close();
    exchange.close();
  }

  private static byte[] readAllBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        outputStream.write(buffer, 0, read);
      }
      return outputStream.toByteArray();
    } finally {
      inputStream.close();
      outputStream.close();
    }
  }
}
