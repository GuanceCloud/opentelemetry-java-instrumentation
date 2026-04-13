/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import io.opentelemetry.instrumentation.profiling.ProfileSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import jdk.jfr.Recording;

final class JfrProfileSnapshot implements ProfileSnapshot {

  private final Recording recording;
  private final Instant startTime;
  private final Instant endTime;

  private JfrProfileSnapshot(Recording recording, Instant startTime, Instant endTime) {
    this.recording = recording;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  static JfrProfileSnapshot create(Recording snapshot, Instant startTime) {
    Instant endTime = snapshot.getStopTime() != null ? snapshot.getStopTime() : Instant.now();
    return new JfrProfileSnapshot(snapshot, startTime, endTime);
  }

  @Override
  public Instant getStartTime() {
    return startTime;
  }

  @Override
  public Instant getEndTime() {
    return endTime;
  }

  @Override
  public String getFormat() {
    return "jfr";
  }

  @Override
  public InputStream openStream() throws IOException {
    return recording.getStream(startTime, endTime);
  }

  @Override
  public void close() {
    recording.close();
  }
}
