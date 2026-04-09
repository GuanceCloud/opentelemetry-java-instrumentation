/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.time.Duration;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class ProfilingConfig {

  private final Duration interval;
  private final Duration startupDelay;
  private final Duration maxAge;
  private final long maxSize;
  private final int stackDepth;
  private final Path tempDir;
  private final boolean memoryEnabled;
  private final Boolean memoryAllocationSampling;
  private final Boolean memoryOldObjectSampling;

  @SuppressWarnings("TooManyParameters")
  public ProfilingConfig(
      Duration interval,
      Duration startupDelay,
      Duration maxAge,
      long maxSize,
      int stackDepth,
      Path tempDir,
      boolean memoryEnabled,
      Boolean memoryAllocationSampling,
      Boolean memoryOldObjectSampling) {
    this.interval = requireNonNull(interval, "interval");
    this.startupDelay = requireNonNull(startupDelay, "startupDelay");
    this.maxAge = requireNonNull(maxAge, "maxAge");
    if (interval.isZero() || interval.isNegative()) {
      throw new IllegalArgumentException("interval must be positive");
    }
    if (startupDelay.isNegative()) {
      throw new IllegalArgumentException("startupDelay must not be negative");
    }
    if (maxAge.isZero() || maxAge.isNegative()) {
      throw new IllegalArgumentException("maxAge must be positive");
    }
    if (maxSize < 0) {
      throw new IllegalArgumentException("maxSize must not be negative");
    }
    if (stackDepth <= 0) {
      throw new IllegalArgumentException("stackDepth must be positive");
    }
    this.maxSize = maxSize;
    this.stackDepth = stackDepth;
    this.tempDir = tempDir;
    this.memoryEnabled = memoryEnabled;
    this.memoryAllocationSampling = memoryAllocationSampling;
    this.memoryOldObjectSampling = memoryOldObjectSampling;
  }

  public Duration getInterval() {
    return interval;
  }

  public Duration getStartupDelay() {
    return startupDelay;
  }

  public Duration getMaxAge() {
    return maxAge;
  }

  public long getMaxSize() {
    return maxSize;
  }

  public int getStackDepth() {
    return stackDepth;
  }

  public Path getTempDir() {
    return tempDir;
  }

  boolean isMemoryEnabled() {
    return memoryEnabled;
  }

  Boolean getMemoryAllocationSampling() {
    return memoryAllocationSampling;
  }

  Boolean getMemoryOldObjectSampling() {
    return memoryOldObjectSampling;
  }
}
