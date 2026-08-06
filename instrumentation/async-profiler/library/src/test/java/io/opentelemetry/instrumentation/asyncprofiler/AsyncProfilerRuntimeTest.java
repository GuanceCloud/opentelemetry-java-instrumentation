/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AsyncProfilerRuntimeTest {

  @Test
  void generatesJfrOnClose() throws Exception {
    assertJfrContainsProfilerEvent("wall", "profiler.WallClockSample");
  }

  @Test
  void generatesAllocationJfrOnClose() throws Exception {
    assertJfrContainsProfilerEvent("alloc", "jdk.ObjectAllocationInNewTLAB");
  }

  private static void assertJfrContainsProfilerEvent(String profilerEvent, String expectedJfrEvent)
      throws Exception {
    Assumptions.assumeTrue(LinuxPlatform.current().isPresent(), "Linux async-profiler test only");

    Path outputDir = Files.createTempDirectory("otel-async-profiler-output");
    Path tempDir = Files.createTempDirectory("otel-async-profiler-temp");
    Path outputFile = outputDir.resolve("profile-" + profilerEvent + ".jfr");

    AsyncProfilerRuntime runtime =
        new AsyncProfilerRuntime(
            new AsyncProfilerConfig(Duration.ZERO, profilerEvent, outputFile.toString(), tempDir));
    try {
      runtime.start();
      if (profilerEvent.equals("alloc")) {
        doAllocationWork(Duration.ofMillis(500));
      } else {
        doBusyWork(Duration.ofMillis(500));
      }
    } finally {
      runtime.close();
    }

    waitForFile(outputFile, Duration.ofSeconds(10));
    assertTrue(
        Files.size(outputFile) > 0, "Expected async-profiler to generate a non-empty JFR file");

    String summaryOutput = run(asList(javaExecutable("jfr"), "summary", outputFile.toString()));
    assertTrue(
        summaryOutput.contains(expectedJfrEvent),
        () -> "Expected " + expectedJfrEvent + " in JFR summary:\n" + summaryOutput);
  }

  private static void waitForFile(Path path, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (Files.isRegularFile(path) && Files.size(path) > 0) {
        return;
      }
      Thread.sleep(100);
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

  private static void doAllocationWork(Duration duration) {
    long end = System.nanoTime() + duration.toNanos();
    byte[][] ring = new byte[256][];
    int index = 0;
    long state = 1;
    while (System.nanoTime() < end) {
      ring[index++ & 255] = new byte[8 * 1024];
      state = state * 1103515245L + 12345L;
    }
    if (state == Long.MIN_VALUE) {
      throw new AssertionError("Unreachable");
    }
  }

  private static String javaExecutable(String binaryName) {
    return Paths.get(System.getProperty("java.home"), "bin", binaryName).toString();
  }

  private static String run(List<String> command) throws Exception {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = readAll(process.getInputStream());
    if (process.waitFor() != 0) {
      throw new AssertionError("Command failed:\n" + String.join(" ", command) + "\n" + output);
    }
    return output;
  }

  private static String readAll(InputStream inputStream) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
      }
      return out.toString("UTF-8");
    } finally {
      inputStream.close();
      out.close();
    }
  }
}
