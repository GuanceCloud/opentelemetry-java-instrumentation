/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.profiling;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfilingDatakitExportTest {

  private static HttpServer server;
  private static AtomicReference<String> requestContentType;
  private static AtomicReference<String> requestBody;

  @BeforeAll
  static void setUp() throws Exception {
    Class<?> flightRecorderClass = Class.forName("jdk.jfr.FlightRecorder");
    boolean available = (Boolean) flightRecorderClass.getMethod("isAvailable").invoke(null);
    Assumptions.assumeTrue(available, "JFR not available");

    requestContentType = new AtomicReference<>();
    requestBody = new AtomicReference<>();

    int port = Integer.parseInt(System.getProperty("profiling.datakit.test.port"));
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.createContext(
        "/profiling/v1/input",
        exchange -> {
          requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          requestBody.set(readBody(exchange));
          byte[] response = "ok".getBytes(UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try {
            exchange.getResponseBody().write(response);
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  @AfterAll
  static void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void shouldExportProfilesToDatakitEndpoint() {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              doBusyWork(Duration.ofMillis(250));

              assertThat(requestContentType.get()).startsWith("multipart/form-data; boundary=");
              String body = requestBody.get();
              assertThat(body).isNotBlank();
              assertThat(body).contains("name=\"main\"; filename=\"main.jfr\"");
              assertThat(body).contains("name=\"event\"; filename=\"event.json\"");
              assertThat(body).contains("\"attachments\":[\"main.jfr\"]");
              assertThat(body).contains("\"family\":\"java\"");
              assertThat(body).contains("\"language\":\"java\"");
              assertThat(body).contains("\"format\":\"jfr\"");
              assertThat(body).contains("\"tags_profiler\":\"service:profiling-test-service");
              assertThat(body).contains("env:prod");
              assertThat(body).contains("version:1.2.3");
              assertThat(body).contains("host:profiling-host");
              assertThat(body).contains("library_type:opentelemetry-javaagent");
              assertThat(body).contains("language:jvm");
              assertThat(body).contains("process_id:");
            });
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    try (InputStream inputStream = exchange.getRequestBody();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), UTF_8);
    }
  }

  private static void doBusyWork(Duration duration) {
    long end = System.nanoTime() + duration.toNanos();
    long state = 1;
    while (System.nanoTime() < end) {
      state = state * 1664525L + 1013904223L;
    }
    if (state == Long.MIN_VALUE) {
      throw new AssertionError("Unreachable");
    }
  }
}
