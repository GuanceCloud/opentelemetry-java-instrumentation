/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.profiling.ProfileSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DatakitProfileExporterAdapterTest {

  @Test
  void uploadsMultipartJfrPayload() throws Exception {
    AtomicReference<String> contentType = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    CountDownLatch requestReceived = new CountDownLatch(1);

    try (TestHttpServer server =
        TestHttpServer.create(
            exchange -> {
              contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
              requestBody.set(readRequestBody(exchange));
              writeResponse(exchange, 200, "ok");
              requestReceived.countDown();
            })) {
      Map<String, String> tags = new LinkedHashMap<>();
      tags.put("service", "checkout");
      tags.put("env", "prod");
      tags.put("version", "1,2,3");
      tags.put("host", "app-host");
      tags.put("process_id", "4242");
      tags.put("library_type", "opentelemetry-javaagent");
      tags.put("library_version", "2.22.0");

      DatakitProfileExporterAdapter adapter =
          new DatakitProfileExporterAdapter(
              server.url("/profiling/v1/input"), Duration.ofSeconds(5), tags);

      adapter.export(new TestProfileSnapshot("jfr-test-data".getBytes(UTF_8)));

      assertTrue(requestReceived.await(5, SECONDS));
      assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));

      String body = requestBody.get();
      assertTrue(body.contains("name=\"main\"; filename=\"main.jfr\""));
      assertTrue(body.contains("name=\"event\"; filename=\"event.json\""));
      assertTrue(body.contains("Content-Type: application/json"));
      assertTrue(body.contains("jfr-test-data"));
      assertTrue(body.contains("\"attachments\":[\"main.jfr\"]"));
      assertTrue(body.contains("\"start\":\"2026-01-01T00:00:00Z\""));
      assertTrue(body.contains("\"end\":\"2026-01-01T00:00:05Z\""));
      assertTrue(body.contains("\"family\":\"java\""));
      assertTrue(body.contains("\"language\":\"java\""));
      assertTrue(body.contains("\"format\":\"jfr\""));
      assertTrue(
          body.contains(
              "\"tags_profiler\":\"service:checkout,env:prod,version:1_2_3,host:app-host,process_id:4242,library_type:opentelemetry-javaagent,library_version:2.22.0\""));
    }
  }

  @Test
  void throwsOnNonSuccessResponse() throws Exception {
    try (TestHttpServer server =
        TestHttpServer.create(exchange -> writeResponse(exchange, 500, "upload failed"))) {
      DatakitProfileExporterAdapter adapter =
          new DatakitProfileExporterAdapter(
              server.url("/profiling/v1/input"),
              Duration.ofSeconds(5),
              new LinkedHashMap<String, String>());

      IOException exception =
          assertThrows(
              IOException.class,
              () -> adapter.export(new TestProfileSnapshot("jfr-test-data".getBytes(UTF_8))));

      assertTrue(exception.getMessage().contains("status 500"));
      assertTrue(exception.getMessage().contains("upload failed"));
    }
  }

  private static String readRequestBody(HttpExchange exchange) throws IOException {
    try (InputStream inputStream = exchange.getRequestBody()) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), UTF_8);
    }
  }

  private static void writeResponse(HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try {
      exchange.getResponseBody().write(bytes);
    } finally {
      exchange.close();
    }
  }

  private static final class TestProfileSnapshot implements ProfileSnapshot {

    private final byte[] bytes;

    private TestProfileSnapshot(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public Instant getStartTime() {
      return Instant.parse("2026-01-01T00:00:00Z");
    }

    @Override
    public Instant getEndTime() {
      return Instant.parse("2026-01-01T00:00:05Z");
    }

    @Override
    public String getFormat() {
      return "jfr";
    }

    @Override
    public InputStream openStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void close() {
      // Intentionally empty.
    }
  }

  private static final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    private TestHttpServer(HttpServer server) {
      this.server = server;
    }

    static TestHttpServer create(ExchangeHandler handler) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext(
          "/profiling/v1/input",
          exchange -> {
            try {
              handler.handle(exchange);
            } finally {
              if (exchange.getResponseBody() != null) {
                exchange.getResponseBody().close();
              }
            }
          });
      server.start();
      return new TestHttpServer(server);
    }

    URL url(String path) throws IOException {
      return new URL("http", "127.0.0.1", server.getAddress().getPort(), path);
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  @FunctionalInterface
  private interface ExchangeHandler {

    void handle(HttpExchange exchange) throws IOException;
  }
}
