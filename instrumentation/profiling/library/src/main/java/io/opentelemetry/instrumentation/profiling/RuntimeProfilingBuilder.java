/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.profiling.internal.JfrSupport;
import io.opentelemetry.instrumentation.profiling.internal.ProfilingConfig;
import java.nio.file.Path;
import java.time.Duration;

/** Builder for {@link RuntimeProfiling}. */
public final class RuntimeProfilingBuilder {

  private Duration interval = Duration.ofMinutes(1);
  private Duration startupDelay = Duration.ZERO;
  private Duration maxAge = Duration.ofMinutes(5);
  private long maxSize;
  private int stackDepth = 64;
  private Path tempDir;
  private boolean memoryEnabled;
  private Boolean memoryAllocationSampling;
  private Boolean memoryOldObjectSampling;
  private ProfileExporterAdapter exporterAdapter = snapshot -> {};

  public RuntimeProfiling build() {
    ProfilingConfig config =
        new ProfilingConfig(
            interval,
            startupDelay,
            maxAge,
            maxSize,
            stackDepth,
            tempDir,
            memoryEnabled,
            memoryAllocationSampling,
            memoryOldObjectSampling);
    return JfrSupport.build(config, exporterAdapter);
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder interval(Duration interval) {
    this.interval = requireNonNull(interval, "interval");
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder startupDelay(Duration startupDelay) {
    this.startupDelay = requireNonNull(startupDelay, "startupDelay");
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder maxAge(Duration maxAge) {
    this.maxAge = requireNonNull(maxAge, "maxAge");
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder maxSize(long maxSize) {
    this.maxSize = maxSize;
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder stackDepth(int stackDepth) {
    this.stackDepth = stackDepth;
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder tempDir(Path tempDir) {
    this.tempDir = tempDir;
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder memoryEnabled(boolean memoryEnabled) {
    this.memoryEnabled = memoryEnabled;
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder memoryAllocationSampling(Boolean memoryAllocationSampling) {
    this.memoryAllocationSampling = memoryAllocationSampling;
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder memoryOldObjectSampling(Boolean memoryOldObjectSampling) {
    this.memoryOldObjectSampling = memoryOldObjectSampling;
    return this;
  }

  @CanIgnoreReturnValue
  public RuntimeProfilingBuilder exporter(ProfileExporterAdapter exporterAdapter) {
    this.exporterAdapter = requireNonNull(exporterAdapter, "exporterAdapter");
    return this;
  }
}
