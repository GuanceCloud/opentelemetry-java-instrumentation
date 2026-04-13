/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.opentelemetry.instrumentation.profiling.ProfileSnapshot;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JfrRecordingSessionTest {

  @TempDir Path tempDir;

  @Test
  void canSnapshotRecording() throws Exception {
    Assumptions.assumeTrue(JfrSupport.isJfrAvailable(), "JFR not available");
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");

    ProfilingConfig config =
        new ProfilingConfig(
            Duration.ofSeconds(1),
            Duration.ZERO,
            Duration.ofMinutes(5),
            0,
            64,
            tempDir,
            true,
            Boolean.TRUE,
            Boolean.TRUE);
    Map<String, String> settings = JfrRecordingSession.createSettings(config);
    assertEquals("true", settings.get("jdk.ObjectAllocationSample#enabled"));
    assertEquals("true", settings.get("jdk.ObjectCount#enabled"));
    assertEquals("true", settings.get("jdk.ObjectCountAfterGC#enabled"));
    assertEquals("true", settings.get("jdk.NativeMemoryUsage#enabled"));
    assertEquals("true", settings.get("jdk.NativeMemoryUsageTotal#enabled"));
    assertEquals("true", settings.get("jdk.GCHeapSummary#enabled"));

    try (JfrRecordingSession session = new JfrRecordingSession(config)) {
      session.start();

      // Produce some work before taking the snapshot.
      for (int i = 0; i < 100_000; i++) {
        Math.log(i + 1);
      }

      try (ProfileSnapshot snapshot = session.snapshot(Instant.now().minusSeconds(1));
          InputStream stream = snapshot.openStream()) {
        assertEquals("jfr", snapshot.getFormat());
        assertNotEquals(-1, stream.read());
      }
    }
  }

  @Test
  void memorySettingsOverrideProfileDefaults() throws Exception {
    Assumptions.assumeTrue(JfrSupport.isJfrAvailable(), "JFR not available");
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");

    ProfilingConfig config =
        new ProfilingConfig(
            Duration.ofSeconds(1),
            Duration.ZERO,
            Duration.ofMinutes(5),
            0,
            64,
            tempDir,
            false,
            Boolean.TRUE,
            Boolean.FALSE);
    Map<String, String> settings = JfrRecordingSession.createSettings(config);

    assertEquals("true", settings.get("jdk.ObjectAllocationSample#enabled"));
    assertEquals("true", settings.get("jdk.ObjectAllocationInNewTLAB#enabled"));
    assertEquals("true", settings.get("jdk.ObjectAllocationOutsideTLAB#enabled"));
    assertEquals("false", settings.get("jdk.OldObjectSample#enabled"));
  }

  @Test
  void stopReturnsSnapshotWhenRecordingWasAlreadyStopped() throws Exception {
    Assumptions.assumeTrue(JfrSupport.isJfrAvailable(), "JFR not available");
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");

    ProfilingConfig config =
        new ProfilingConfig(
            Duration.ofSeconds(1),
            Duration.ZERO,
            Duration.ofMinutes(5),
            0,
            64,
            tempDir,
            false,
            null,
            null);

    try (JfrRecordingSession session = new JfrRecordingSession(config)) {
      session.start();

      Recording recording = getRecording(session);
      recording.stop();

      try (ProfileSnapshot snapshot = session.stop(Instant.now().minusSeconds(1));
          InputStream stream = snapshot.openStream()) {
        assertEquals("jfr", snapshot.getFormat());
        assertNotEquals(-1, stream.read());
      }
    }
  }

  private static Recording getRecording(JfrRecordingSession session) throws Exception {
    Field field = JfrRecordingSession.class.getDeclaredField("recording");
    field.setAccessible(true);
    return (Recording) field.get(session);
  }
}
