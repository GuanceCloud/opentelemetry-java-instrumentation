/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.profiling;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfilingFileExportTest {

  private static Path outputDirectory;

  @BeforeAll
  static void setUp() throws Exception {
    Class<?> flightRecorderClass = Class.forName("jdk.jfr.FlightRecorder");
    boolean available = (Boolean) flightRecorderClass.getMethod("isAvailable").invoke(null);
    Assumptions.assumeTrue(available, "JFR not available");

    String configuredPath = System.getProperty("otel.profiling.experimental.file-export.path");
    if (configuredPath == null || configuredPath.isEmpty()) {
      configuredPath = System.getenv("OTEL_PROFILING_EXPERIMENTAL_FILE_EXPORT_PATH");
    }
    if (configuredPath == null || configuredPath.isEmpty()) {
      configuredPath =
          System.getProperty("otel.instrumentation.profiling.experimental.file-export.path");
    }
    outputDirectory = Paths.get(configuredPath);
  }

  @Test
  void shouldExportProfilesToDisk() {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              doBusyWork(Duration.ofMillis(250));

              List<Path> exportedProfiles = exportedProfiles();
              assertThat(exportedProfiles).isNotEmpty();
              assertThat(exportedProfiles)
                  .allSatisfy(
                      path -> {
                        assertThat(path.getFileName().toString()).endsWith(".jfr");
                        assertThat(path).isRegularFile();
                        assertThat(Files.size(path)).isGreaterThan(0);
                      });
            });
  }

  private static List<Path> exportedProfiles() throws Exception {
    try (Stream<Path> files = Files.list(outputDirectory)) {
      return files.collect(toList());
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
