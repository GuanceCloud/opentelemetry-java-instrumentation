/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import one.profiler.AsyncProfiler;

/** Controls async-profiler lifecycle for the current JVM. */
public final class AsyncProfilerRuntime implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(AsyncProfilerRuntime.class.getName());

  private final AsyncProfilerConfig config;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "OpenTelemetry AsyncProfiler");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  private volatile AsyncProfiler profiler;
  private volatile String resolvedOutput;
  private volatile Instant profileStartTime;
  private volatile ScheduledFuture<?> exportTask;

  public AsyncProfilerRuntime(AsyncProfilerConfig config) {
    this.config = config;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    Duration startupDelay = config.getStartupDelay();
    long startupDelayMillis = startupDelay.toMillis();
    if (startupDelayMillis == 0) {
      startProfiling();
    } else {
      scheduler.schedule(this::startProfiling, startupDelayMillis, MILLISECONDS);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    scheduler.shutdownNow();
    stopProfiling();
  }

  private synchronized void startProfiling() {
    if (closed.get()) {
      return;
    }

    try {
      LinuxPlatform platform = requirePlatformSupport();
      String output = config.resolveOutputPath();
      ensureOutputDirectoryExists(output);
      Path nativeLibrary = NativeLibraryExtractor.extract(platform, config.getTempDir());
      AsyncProfiler asyncProfiler =
          AsyncProfiler.getInstance(nativeLibrary.toAbsolutePath().toString());
      Instant startTime = Instant.now();
      asyncProfiler.execute(config.toStartCommand(output));
      profiler = asyncProfiler;
      resolvedOutput = output;
      profileStartTime = startTime;
      scheduleExportIfNeeded();
    } catch (Throwable throwable) {
      logger.log(WARNING, "Unable to start async-profiler runtime", throwable);
    }
  }

  private synchronized void stopProfiling() {
    cancelScheduledExport();
    AsyncProfiler current = profiler;
    String currentOutput = resolvedOutput;
    Instant currentStart = profileStartTime;
    profiler = null;
    resolvedOutput = null;
    profileStartTime = null;
    if (current == null) {
      return;
    }

    Instant endTime = Instant.now();
    try {
      current.execute(config.toStopCommand(currentOutput));
    } catch (IOException | IllegalStateException exception) {
      logger.log(WARNING, "Unable to stop async-profiler runtime", exception);
      return;
    }

    if (currentOutput == null || currentStart == null) {
      return;
    }

    try {
      Path outputPath = Paths.get(currentOutput);
      if (!Files.isRegularFile(outputPath) || Files.size(outputPath) == 0) {
        return;
      }
      config
          .getExporter()
          .export(new ProfileArtifact(outputPath, currentStart, endTime, config.getOutputFormat()));
    } catch (IOException | RuntimeException exception) {
      logger.log(WARNING, "Unable to export async-profiler output", exception);
    }
  }

  private void rotateProfiling() {
    if (closed.get()) {
      return;
    }
    stopProfiling();
    if (!closed.get()) {
      startProfiling();
    }
  }

  private static LinuxPlatform requirePlatformSupport() {
    Optional<LinuxPlatform> platform = LinuxPlatform.current();
    if (!platform.isPresent()) {
      throw new IllegalStateException(
          "async-profiler integration currently supports Linux x64 and Linux arm64 only");
    }
    return platform.get();
  }

  private static void ensureOutputDirectoryExists(String output) throws IOException {
    Path outputPath = Paths.get(output).toAbsolutePath();
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  private void scheduleExportIfNeeded() {
    if (!config.hasPeriodicExport()) {
      return;
    }
    long exportIntervalMillis = config.getExportInterval().toMillis();
    exportTask = scheduler.schedule(this::rotateProfiling, exportIntervalMillis, MILLISECONDS);
  }

  private void cancelScheduledExport() {
    ScheduledFuture<?> scheduledExport = exportTask;
    exportTask = null;
    if (scheduledExport != null) {
      scheduledExport.cancel(false);
    }
  }
}
