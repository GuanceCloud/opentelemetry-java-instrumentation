/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.profiling.ProfileExporterAdapter;
import io.opentelemetry.instrumentation.profiling.ProfileSnapshot;
import io.opentelemetry.instrumentation.profiling.RuntimeProfiling;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

final class JfrRuntimeProfiling implements RuntimeProfiling {

  private static final Logger logger = Logger.getLogger(JfrRuntimeProfiling.class.getName());

  private final ProfilingConfig config;
  private final ProfileExporterAdapter exporterAdapter;
  private final ProfilingScheduler scheduler =
      new ProfilingScheduler("OpenTelemetry Profiling Runner");
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean exported = new AtomicBoolean();

  private volatile JfrRecordingSession recordingSession;
  private volatile Instant lastSnapshotStart;

  JfrRuntimeProfiling(ProfilingConfig config, ProfileExporterAdapter exporterAdapter) {
    this.config = config;
    this.exporterAdapter = exporterAdapter;
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    long startupDelayMillis = config.getStartupDelay().toMillis();
    if (startupDelayMillis == 0) {
      startRecording();
    } else {
      scheduler.schedule(this::startRecording, startupDelayMillis, MILLISECONDS);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    scheduler.close();
    if (exported.get()) {
      closeRecordingSession();
    } else {
      exportFinalRecording();
    }
  }

  private void startRecording() {
    if (closed.get()) {
      return;
    }

    try {
      JfrRecordingSession session = new JfrRecordingSession(config);
      session.start();
      recordingSession = session;
      lastSnapshotStart = Instant.now();
      long intervalMillis = config.getInterval().toMillis();
      scheduler.scheduleAtFixedRate(
          this::exportSnapshot, intervalMillis, intervalMillis, MILLISECONDS);
    } catch (RuntimeException exception) {
      logger.log(WARNING, "Unable to start JFR profiling session", exception);
      closeRecordingSession();
    }
  }

  private void exportSnapshot() {
    exportCurrentRecording(false);
  }

  private void exportFinalRecording() {
    exportCurrentRecording(true);
  }

  private void exportCurrentRecording(boolean finalExport) {
    JfrRecordingSession session = recordingSession;
    if (session == null) {
      return;
    }
    if (finalExport) {
      recordingSession = null;
    }

    Instant snapshotStart = lastSnapshotStart != null ? lastSnapshotStart : Instant.now();
    try (ProfileSnapshot snapshot =
        finalExport ? session.stop(snapshotStart) : session.snapshot(snapshotStart)) {
      lastSnapshotStart = snapshot.getEndTime().plusNanos(1);
      exporterAdapter.export(snapshot);
      exported.set(true);
    } catch (Exception exception) {
      if (isMissingChunks(exception)) {
        logger.log(
            FINE,
            finalExport
                ? "Skipping final profiling snapshot because JFR has not produced chunks yet"
                : "Skipping profiling snapshot because JFR has not produced chunks yet");
        return;
      }
      logger.log(
          WARNING,
          finalExport
              ? "Unable to export final profiling snapshot"
              : "Unable to export profiling snapshot",
          exception);
    } finally {
      if (finalExport) {
        session.close();
      }
    }
  }

  private void closeRecordingSession() {
    JfrRecordingSession session = recordingSession;
    recordingSession = null;
    if (session != null) {
      session.close();
    }
  }

  private static boolean isMissingChunks(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message != null
          && (message.contains("No chunks") || message.contains("Missing chunkfile"))) {
        return true;
      }
    }
    return false;
  }
}
