/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AsyncProfilerAgentSmokeTest {

  @Test
  void shouldWriteWallClockSamplesWhenStartedViaJavaagent() throws Exception {
    assertJfrContainsProfilerEvent("wall", "cpu", "profiler.WallClockSample");
  }

  @Test
  void shouldWriteAllocationSamplesWhenStartedViaJavaagent() throws Exception {
    assertJfrContainsProfilerEvent("alloc", "alloc", "jdk.ObjectAllocationInNewTLAB");
  }

  @Test
  void shouldWriteLiveObjectSamplesWhenMemoryTopStatsConfiguredViaJavaagent() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
        "Linux smoke test only");

    Path outputDir = Files.createTempDirectory("otel-async-profiler-live-memory-output");
    Path tempDir = Files.createTempDirectory("otel-async-profiler-live-memory-native");
    String outputPattern = outputDir.resolve("profile-%p.jfr").toString();

    Process process =
        new ProcessBuilder(
                javaExecutable("java"),
                "-javaagent:" + javaagentJar(),
                "-Dotel.javaagent.experimental.initializer.jar=" + initializerJar(),
                "-Dotel.traces.exporter=none",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-Dotel.profiling.enabled=true",
                "-Dotel.profiling.memory.enabled=true",
                "-Dotel.profiling.memory.interval=128k",
                "-Dotel.profiling.memory.top-stats=20",
                "-Dotel.profiling.async.output=" + outputPattern,
                "-Dotel.profiling.async.temp-dir=" + tempDir,
                "-cp",
                System.getProperty("java.class.path"),
                AsyncProfilerSmokeMain.class.getName(),
                "alloc")
            .redirectErrorStream(true)
            .start();

    String output = readAll(process.getInputStream());
    assertEquals(0, process.waitFor(), () -> "Child JVM failed:\n" + output);

    Path jfrFile = waitForSingleJfr(outputDir, Duration.ofSeconds(10));
    String summaryOutput = run(asList(javaExecutable("jfr"), "summary", jfrFile.toString()));
    assertTrue(
        summaryOutput.contains("profiler.LiveObject"),
        () -> "Expected profiler.LiveObject in JFR summary:\n" + summaryOutput);
  }

  @Test
  void shouldWriteExceptionEventsWhenEnabledViaJavaagent() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
        "Linux smoke test only");

    Path outputDir = Files.createTempDirectory("otel-async-profiler-exception-output");
    Path tempDir = Files.createTempDirectory("otel-async-profiler-exception-native");
    String outputPattern = outputDir.resolve("profile-%p.jfr").toString();

    Process process =
        new ProcessBuilder(
                javaExecutable("java"),
                "-javaagent:" + javaagentJar(),
                "-Dotel.javaagent.experimental.initializer.jar=" + initializerJar(),
                "-Dotel.traces.exporter=none",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-Dotel.profiling.enabled=true",
                "-Dotel.profiling.exception.enabled=true",
                "-Dotel.profiling.async.event=cpu",
                "-Dotel.profiling.async.output=" + outputPattern,
                "-Dotel.profiling.async.temp-dir=" + tempDir,
                "-cp",
                System.getProperty("java.class.path"),
                AsyncProfilerSmokeMain.class.getName(),
                "exception")
            .redirectErrorStream(true)
            .start();

    String output = readAll(process.getInputStream());
    assertEquals(0, process.waitFor(), () -> "Child JVM failed:\n" + output);

    Path jfrFile = waitForSingleJfr(outputDir, Duration.ofSeconds(10));
    String summaryOutput = run(asList(javaExecutable("jfr"), "summary", jfrFile.toString()));
    assertTrue(
        summaryOutput.contains("jdk.JavaExceptionThrow"),
        () -> "Expected jdk.JavaExceptionThrow in JFR summary:\n" + summaryOutput);
  }

  @Test
  void shouldUploadProfilesToDatakitEndpoint() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
        "Linux smoke test only");

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

    Path outputDir = Files.createTempDirectory("otel-async-profiler-datakit-output");
    Path tempDir = Files.createTempDirectory("otel-async-profiler-datakit-native");
    String outputPattern = outputDir.resolve("profile-%p.jfr").toString();

    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder(
                  javaExecutable("java"),
                  "-javaagent:" + javaagentJar(),
                  "-Dotel.javaagent.experimental.initializer.jar=" + initializerJar(),
                  "-Dotel.traces.exporter=none",
                  "-Dotel.metrics.exporter=none",
                  "-Dotel.logs.exporter=none",
                  "-Dotel.service.name=smoke-service",
                  "-cp",
                  System.getProperty("java.class.path"),
                  AsyncProfilerSmokeMain.class.getName(),
                  "cpu")
              .redirectErrorStream(true);
      processBuilder.environment().put("OTEL_PROFILING_ENABLED", "true");
      processBuilder.environment().put("OTEL_PROFILING_EXPORTER", "datakit");
      processBuilder
          .environment()
          .put(
              "OTEL_PROFILING_ENDPOINT",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/profiling/v1/input");
      processBuilder.environment().put("OTEL_PROFILING_ASYNC_EVENT", "wall");
      processBuilder.environment().put("OTEL_PROFILING_ASYNC_OUTPUT", outputPattern);
      processBuilder.environment().put("OTEL_PROFILING_ASYNC_TEMP_DIR", tempDir.toString());

      Process process = processBuilder.start();

      String output = readAll(process.getInputStream());
      assertEquals(0, process.waitFor(), () -> "Child JVM failed:\n" + output);
    } finally {
      server.stop(0);
    }

    String body = requestBody.get();
    assertTrue(body != null && body.contains("name=\"main\"; filename=\"main.jfr\""));
    assertTrue(body.contains("\"format\":\"jfr\""));
    assertTrue(body.contains("service:smoke-service"));
    assertTrue(body.contains("library_type:async_profiler"));
    assertTrue(body.contains("library_version:4.4"));
  }

  @Test
  void shouldUploadProfilesToOtlpEndpoint() throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
        "Linux smoke test only");

    AtomicInteger requestCount = new AtomicInteger();
    AtomicReference<String> authHeader = new AtomicReference<>();
    AtomicReference<Integer> contentLength = new AtomicReference<>();
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/profiles",
        exchange -> {
          requestCount.incrementAndGet();
          authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          contentLength.set(readAllBytes(exchange.getRequestBody()).length);
          respond(exchange);
        });
    server.start();

    Path tempDir = Files.createTempDirectory("otel-async-profiler-otlp-native");

    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder(
                  javaExecutable("java"),
                  "-javaagent:" + javaagentJar(),
                  "-Dotel.javaagent.experimental.initializer.jar=" + initializerJar(),
                  "-Dotel.traces.exporter=none",
                  "-Dotel.metrics.exporter=none",
                  "-Dotel.logs.exporter=none",
                  "-cp",
                  System.getProperty("java.class.path"),
                  AsyncProfilerSmokeMain.class.getName(),
                  "cpu")
              .redirectErrorStream(true);
      processBuilder.environment().put("OTEL_PROFILING_ENABLED", "true");
      processBuilder.environment().put("OTEL_PROFILING_EXPORTER", "otlp");
      processBuilder.environment().put("OTEL_PROFILING_SAMPLE_INTERVAL", "10ms");
      processBuilder.environment().put("OTEL_PROFILING_EXPORT_INTERVAL", "1s");
      processBuilder.environment().put("OTEL_PROFILING_MAX_FRAMES", "64");
      processBuilder.environment().put("OTEL_PROFILING_MEMORY_ENABLED", "true");
      processBuilder.environment().put("OTEL_PROFILING_MEMORY_INTERVAL", "128k");
      processBuilder.environment().put("OTEL_PROFILING_ASYNC_TEMP_DIR", tempDir.toString());
      processBuilder.environment().put("OTEL_EXPORTER_OTLP_PROFILES_PROTOCOL", "http/protobuf");
      processBuilder
          .environment()
          .put(
              "OTEL_EXPORTER_OTLP_PROFILES_ENDPOINT",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/profiles");
      processBuilder
          .environment()
          .put("OTEL_EXPORTER_OTLP_PROFILES_HEADERS", "Authorization=Bearer smoke-token");

      Process process = processBuilder.start();

      String output = readAll(process.getInputStream());
      assertEquals(0, process.waitFor(), () -> "Child JVM failed:\n" + output);
    } finally {
      server.stop(0);
    }

    assertTrue(requestCount.get() >= 1, "Expected at least one OTLP profile upload");
    assertEquals("Bearer smoke-token", authHeader.get());
    assertTrue(contentLength.get() != null && contentLength.get().intValue() > 0);
  }

  private static void assertJfrContainsProfilerEvent(
      String profilerEvent, String workload, String expectedJfrEvent) throws Exception {
    Assumptions.assumeTrue(
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
        "Linux smoke test only");

    Path outputDir = Files.createTempDirectory("otel-async-profiler-agent-output");
    Path tempDir = Files.createTempDirectory("otel-async-profiler-agent-native");
    String outputPattern = outputDir.resolve("profile-%p.jfr").toString();

    Process process =
        new ProcessBuilder(
                javaExecutable("java"),
                "-javaagent:" + javaagentJar(),
                "-Dotel.javaagent.experimental.initializer.jar=" + initializerJar(),
                "-Dotel.traces.exporter=none",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
                "-Dotel.profiling.enabled=true",
                "-Dotel.profiling.async.event=" + profilerEvent,
                "-Dotel.profiling.async.output=" + outputPattern,
                "-Dotel.profiling.async.temp-dir=" + tempDir,
                "-cp",
                System.getProperty("java.class.path"),
                AsyncProfilerSmokeMain.class.getName(),
                workload)
            .redirectErrorStream(true)
            .start();

    String output = readAll(process.getInputStream());
    assertEquals(0, process.waitFor(), () -> "Child JVM failed:\n" + output);

    Path jfrFile = waitForSingleJfr(outputDir, Duration.ofSeconds(10));
    assertTrue(Files.size(jfrFile) > 0, "Expected non-empty JFR file");

    String summaryOutput = run(asList(javaExecutable("jfr"), "summary", jfrFile.toString()));
    assertTrue(
        summaryOutput.contains(expectedJfrEvent),
        () -> "Expected " + expectedJfrEvent + " in JFR summary:\n" + summaryOutput);
  }

  private static String javaagentJar() {
    return requiredProperty("otel.javaagent.testing.javaagent-jar-path");
  }

  private static String initializerJar() {
    return requiredProperty("otel.javaagent.experimental.initializer.jar");
  }

  private static String requiredProperty(String propertyName) {
    return requireNonNull(
        System.getProperty(propertyName), () -> "Missing system property: " + propertyName);
  }

  private static String javaExecutable(String binaryName) {
    return Paths.get(System.getProperty("java.home"), "bin", binaryName).toString();
  }

  private static Path waitForSingleJfr(Path outputDir, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      try (Stream<Path> stream = Files.list(outputDir)) {
        List<Path> files =
            stream.filter(path -> path.getFileName().toString().endsWith(".jfr")).collect(toList());
        if (!files.isEmpty()) {
          return files.get(0);
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Expected JFR file in " + outputDir);
  }

  private static String readAll(InputStream inputStream) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      }
      return new String(out.toByteArray(), UTF_8);
    } finally {
      inputStream.close();
      out.close();
    }
  }

  private static byte[] readAllBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    } finally {
      inputStream.close();
      out.close();
    }
  }

  private static String run(List<String> command) throws Exception {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = readAll(process.getInputStream());
    assertEquals(
        0,
        process.waitFor(),
        () -> "Command failed:\n" + String.join(" ", command) + "\n" + output);
    return output;
  }

  private static void respond(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(200, 0);
    exchange.getResponseBody().close();
    exchange.close();
  }
}
