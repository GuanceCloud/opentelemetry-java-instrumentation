/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AsyncProfilerConfigTest {

  @Test
  void shouldBuildJfrStartCommandWithExtendedOptions() {
    AsyncProfilerConfig config =
        AsyncProfilerConfig.builder(Duration.ZERO, "cpu", "/tmp/profile-%p.jfr", Paths.get("/tmp"))
            .exportInterval(Duration.ofSeconds(30))
            .exporter(ProfileExporter.noop())
            .outputFormat("jfr")
            .sampleInterval("10ms")
            .maxFrames(Integer.valueOf(256))
            .lockEnabled(true)
            .lockThreshold("5ms")
            .memoryEnabled(true)
            .memoryInterval("128k")
            .memoryTopStats(10)
            .exceptionEnabled(true)
            .exceptionSamplingInterval("1ms")
            .exceptionCollectMessage(true)
            .build();

    String command = config.toStartCommand("/tmp/profile-123.jfr");

    assertEquals("stop", config.toStopCommand("/tmp/profile-123.jfr"));
    assertTrue(command.contains("start,jfr,event=cpu"));
    assertTrue(command.contains("interval=10ms"));
    assertTrue(command.contains("jstackdepth=256"));
    assertTrue(command.contains("alloc=128k"));
    assertTrue(command.contains(",live"));
    assertTrue(command.contains("lock=5ms"));
    assertTrue(command.contains("jfrsync=+jdk.JavaExceptionThrow#threshold=1ms"));
    assertTrue(command.contains("file=/tmp/profile-123.jfr"));
  }

  @Test
  void shouldBuildOtlpRotationCommands() {
    AsyncProfilerConfig config =
        AsyncProfilerConfig.builder(Duration.ZERO, "cpu", "/tmp/profile-%p.otlp", Paths.get("/tmp"))
            .exportInterval(Duration.ofSeconds(5))
            .exporter(ProfileExporter.noop())
            .outputFormat("otlp")
            .sampleInterval("10ms")
            .maxFrames(Integer.valueOf(128))
            .memoryEnabled(true)
            .memoryInterval("128k")
            .exceptionCollectMessage(true)
            .build();

    assertTrue(config.hasPeriodicExport());
    assertEquals("start,event=alloc,alloc=128k,jstackdepth=128", config.toStartCommand("/tmp/x"));
    assertEquals("stop,otlp,file=/tmp/x", config.toStopCommand("/tmp/x"));
  }

  @Test
  void shouldEnableLiveHeapProfilingForJfrMemoryMode() {
    AsyncProfilerConfig config =
        AsyncProfilerConfig.builder(Duration.ZERO, "cpu", "/tmp/profile-%p.jfr", Paths.get("/tmp"))
            .memoryEnabled(true)
            .memoryInterval("256k")
            .build();

    String command = config.toStartCommand("/tmp/profile-123.jfr");

    assertTrue(command.contains("alloc=256k"));
  }

  @Test
  void shouldEnableLiveHeapProfilingWhenMemoryTopStatsConfigured() {
    AsyncProfilerConfig config =
        AsyncProfilerConfig.builder(Duration.ZERO, "cpu", "/tmp/profile-%p.jfr", Paths.get("/tmp"))
            .memoryEnabled(true)
            .memoryInterval("256k")
            .memoryTopStats(20)
            .build();

    String command = config.toStartCommand("/tmp/profile-123.jfr");

    assertTrue(command.contains("alloc=256k"));
    assertTrue(command.contains(",live"));
  }
}
