/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.ALL;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpMetaExporterTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void sendsGzipJsonRequest() throws Exception {
    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<byte[]> requestBody = new AtomicReference<>();
    AtomicReference<String> contentEncoding = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    HttpServer server = startServer();
    server.createContext(
        "/v1/meta",
        exchange -> {
          requestBody.set(readAll(exchange.getRequestBody()));
          contentEncoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
          received.countDown();
        });

    HttpMetaExporter exporter =
        exporter(server, singletonMap("Authorization", "Bearer token"), true, 0, ignored -> {});

    assertThat(exporter.export(request())).isEqualTo(MetaExportResult.SUCCESS);
    assertThat(received.await(5, SECONDS)).isTrue();
    assertThat(contentEncoding.get()).isEqualTo("gzip");
    assertThat(authorization.get()).isEqualTo("Bearer token");

    byte[] uncompressed = gunzip(requestBody.get());
    JsonNode root = new ObjectMapper().readTree(new String(uncompressed, UTF_8));
    assertThat(root.get("events").get(0).get("request_type").asText()).isEqualTo("app-started");
  }

  @Test
  void retriesRetryableResponse() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = startServer();
    server.createContext(
        "/v1/meta",
        exchange -> {
          int status = requests.incrementAndGet() == 1 ? 500 : 204;
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });

    HttpMetaExporter exporter = exporter(server, emptyMap(), false, 1, ignored -> {});

    List<LogRecord> logs =
        captureLogs(
            () -> assertThat(exporter.export(request())).isEqualTo(MetaExportResult.SUCCESS));
    assertThat(requests).hasValue(2);
    assertThat(logMessages(logs))
        .filteredOn(message -> message.startsWith("Meta export succeeded"))
        .singleElement()
        .asString()
        .contains("status_code=204", "seq_id=1", "event_count=1", "attempts=2");
  }

  @Test
  void doesNotRetryPermanentClientError() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = startServer();
    server.createContext(
        "/v1/meta",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(400, -1);
          exchange.close();
        });

    HttpMetaExporter exporter = exporter(server, emptyMap(), false, 3, ignored -> {});

    List<LogRecord> logs =
        captureLogs(
            () ->
                assertThat(exporter.export(request()))
                    .isEqualTo(MetaExportResult.PERMANENT_FAILURE));
    assertThat(requests).hasValue(1);
    assertThat(logMessages(logs))
        .filteredOn(message -> message.startsWith("Meta export failed"))
        .singleElement()
        .asString()
        .contains(
            "status_code=400", "seq_id=1", "event_count=1", "attempts=1", "reason=http_response");
  }

  @Test
  void logsNetworkFailureAfterRetriesAreExhausted() throws Exception {
    HttpServer unavailableServer =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    unavailableServer.start();
    int unavailablePort = unavailableServer.getAddress().getPort();
    unavailableServer.stop(0);
    URL endpoint = new URL("http://127.0.0.1:" + unavailablePort + "/v1/meta");
    HttpMetaExporter exporter =
        new HttpMetaExporter(
            endpoint, emptyMap(), 1000, false, 1, new MetaJsonSerializer(), ignored -> {});

    List<LogRecord> logs =
        captureLogs(
            () ->
                assertThat(exporter.export(request()))
                    .isEqualTo(MetaExportResult.RETRYABLE_FAILURE));

    assertThat(logMessages(logs))
        .filteredOn(message -> message.startsWith("Meta export failed"))
        .singleElement()
        .asString()
        .contains(
            "endpoint=http://127.0.0.1:" + unavailablePort + "/v1/meta",
            "status_code=unavailable",
            "seq_id=1",
            "attempts=2",
            "reason=java.net.ConnectException");
  }

  @Test
  void rejectsRequestLargerThanConfiguredLimit() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = startServer();
    server.createContext(
        "/v1/meta",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    URL endpoint = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/meta");
    HttpMetaExporter exporter =
        new HttpMetaExporter(
            endpoint, emptyMap(), 1000, false, 0, 32, new MetaJsonSerializer(), ignored -> {});

    List<LogRecord> logs =
        captureLogs(
            () ->
                assertThat(exporter.export(request()))
                    .isEqualTo(MetaExportResult.PERMANENT_FAILURE));

    assertThat(requests).hasValue(0);
    assertThat(logMessages(logs))
        .filteredOn(message -> message.startsWith("Meta export failed"))
        .singleElement()
        .asString()
        .contains("attempts=0", "reason=request_too_large");
  }

  @Test
  void limitsUncompressedJsonWhileStreamingGzipRequest() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = startServer();
    server.createContext(
        "/v1/meta",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    URL endpoint = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/meta");
    HttpMetaExporter exporter =
        new HttpMetaExporter(
            endpoint, emptyMap(), 1000, true, 0, 512, new MetaJsonSerializer(), ignored -> {});
    char[] largeValue = new char[16 * 1024];
    Arrays.fill(largeValue, 'a');

    List<LogRecord> logs =
        captureLogs(
            () ->
                assertThat(exporter.export(request(singletonMap("value", new String(largeValue)))))
                    .isEqualTo(MetaExportResult.PERMANENT_FAILURE));

    assertThat(requests).hasValue(0);
    assertThat(logMessages(logs))
        .filteredOn(message -> message.startsWith("Meta export failed"))
        .singleElement()
        .asString()
        .contains("attempts=0", "reason=request_too_large");
  }

  private static HttpServer startServer() throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.start();
    cleanup.deferCleanup(() -> server.stop(0));
    return server;
  }

  private static HttpMetaExporter exporter(
      HttpServer server,
      Map<String, String> headers,
      boolean gzipEnabled,
      int maxRetries,
      HttpMetaExporter.Sleeper sleeper)
      throws Exception {
    URL endpoint = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/meta");
    return new HttpMetaExporter(
        endpoint, headers, 1000, gzipEnabled, maxRetries, new MetaJsonSerializer(), sleeper);
  }

  private static MetaExportRequest request() {
    return request(singletonMap("startup_status", "success"));
  }

  private static MetaExportRequest request(Map<String, ?> payload) {
    Map<String, Object> normalizedPayload = new LinkedHashMap<>();
    normalizedPayload.putAll(payload);
    MetaEvent event = new MetaEvent(1234, "app-started", normalizedPayload);
    return new MetaExportRequest(1, 1234, "runtime-id", emptyMap(), singletonList(event));
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

  private static byte[] gunzip(byte[] value) throws IOException {
    try (GZIPInputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(value))) {
      return readAll(inputStream);
    }
  }

  private static List<LogRecord> captureLogs(Runnable action) {
    Logger exporterLogger = Logger.getLogger(HttpMetaExporter.class.getName());
    Level originalLevel = exporterLogger.getLevel();
    List<LogRecord> records = new CopyOnWriteArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    handler.setLevel(ALL);
    exporterLogger.setLevel(ALL);
    exporterLogger.addHandler(handler);
    try {
      action.run();
    } finally {
      exporterLogger.removeHandler(handler);
      exporterLogger.setLevel(originalLevel);
    }
    return records;
  }

  private static List<String> logMessages(List<LogRecord> records) {
    SimpleFormatter formatter = new SimpleFormatter();
    List<String> messages = new ArrayList<>();
    for (LogRecord record : records) {
      messages.add(formatter.formatMessage(record));
    }
    return messages;
  }
}
