/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

final class JfrRecordingSession implements AutoCloseable {

  private final Recording recording;

  JfrRecordingSession(ProfilingConfig config) {
    this.recording = createRecording(config);
  }

  void start() {
    recording.start();
  }

  JfrProfileSnapshot snapshot(Instant startTime) {
    Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    snapshot.setName(recording.getName());
    return JfrProfileSnapshot.create(snapshot, startTime);
  }

  JfrProfileSnapshot stop(Instant startTime) {
    recording.stop();
    return JfrProfileSnapshot.create(recording, startTime);
  }

  @Override
  public void close() {
    recording.close();
  }

  private static Recording createRecording(ProfilingConfig config) {
    Recording recording = new Recording();
    recording.setName("otel-profiling");
    recording.setToDisk(true);
    if (config.getMaxSize() > 0) {
      recording.setMaxSize(config.getMaxSize());
    }
    recording.setMaxAge(config.getMaxAge());
    try {
      recording.setSettings(createSettings(config));
    } catch (IOException | ParseException exception) {
      throw new IllegalStateException("Unable to load JFR profile configuration", exception);
    }
    return recording;
  }

  static Map<String, String> createSettings(ProfilingConfig config)
      throws IOException, ParseException {
    Map<String, String> settings =
        new HashMap<>(Configuration.getConfiguration("profile").getSettings());
    applyMemorySettings(settings, config);
    return settings;
  }

  private static void applyMemorySettings(Map<String, String> settings, ProfilingConfig config) {
    applyBooleanSetting(
        settings,
        "jdk.ObjectAllocationInNewTLAB#enabled",
        config,
        config.getMemoryAllocationSampling());
    applyBooleanSetting(
        settings,
        "jdk.ObjectAllocationOutsideTLAB#enabled",
        config,
        config.getMemoryAllocationSampling());
    applyBooleanSetting(
        settings,
        "jdk.ObjectAllocationSample#enabled",
        config,
        config.getMemoryAllocationSampling());
    applyBooleanSetting(
        settings, "jdk.OldObjectSample#enabled", config, config.getMemoryOldObjectSampling());
    applyDefaultMemorySetting(settings, "jdk.ObjectCount#enabled", config);
    applyDefaultMemorySetting(settings, "jdk.ObjectCountAfterGC#enabled", config);
    applyDefaultMemorySetting(settings, "jdk.NativeMemoryUsage#enabled", config);
    applyDefaultMemorySetting(settings, "jdk.NativeMemoryUsageTotal#enabled", config);
    applyDefaultMemorySetting(settings, "jdk.GCHeapSummary#enabled", config);
  }

  private static void applyBooleanSetting(
      Map<String, String> settings, String settingName, ProfilingConfig config, Boolean value) {
    if (!config.isMemoryEnabled() && value == null) {
      return;
    }
    settings.put(settingName, Boolean.toString(value != null ? value : config.isMemoryEnabled()));
  }

  private static void applyDefaultMemorySetting(
      Map<String, String> settings, String settingName, ProfilingConfig config) {
    if (config.isMemoryEnabled()) {
      settings.put(settingName, "true");
    }
  }
}
