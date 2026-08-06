/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Configuration for async-profiler runtime integration. */
public final class AsyncProfilerConfig {

  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final Duration startupDelay;
  private final Duration exportInterval;
  private final String event;
  private final String output;
  private final Path tempDir;
  private final ProfileExporter exporter;
  private final String outputFormat;
  private final String sampleInterval;
  private final Integer maxFrames;
  private final boolean lockEnabled;
  private final String lockThreshold;
  private final boolean memoryEnabled;
  private final String memoryInterval;
  private final int memoryTopStats;
  private final boolean exceptionEnabled;
  private final String exceptionSamplingInterval;
  private final boolean exceptionCollectMessage;

  public AsyncProfilerConfig(Duration startupDelay, String event, String output, Path tempDir) {
    this(startupDelay, event, output, tempDir, ProfileExporter.noop());
  }

  public AsyncProfilerConfig(
      Duration startupDelay, String event, String output, Path tempDir, ProfileExporter exporter) {
    this(
        builder(startupDelay, event, output, tempDir)
            .exporter(exporter)
            .outputFormat("jfr")
            .exceptionCollectMessage(true));
  }

  private AsyncProfilerConfig(Builder builder) {
    this.startupDelay = requireNonNull(builder.startupDelay, "startupDelay");
    this.exportInterval = requireNonNull(builder.exportInterval, "exportInterval");
    validateNonBlank(builder.event, "event");
    validateNonBlank(builder.output, "output");
    validateNonBlank(builder.outputFormat, "outputFormat");
    this.event = builder.event;
    this.output = builder.output;
    this.tempDir = requireNonNull(builder.tempDir, "tempDir");
    this.exporter = requireNonNull(builder.exporter, "exporter");
    this.outputFormat = builder.outputFormat;
    this.sampleInterval = trimToNull(builder.sampleInterval);
    this.maxFrames = builder.maxFrames;
    this.lockEnabled = builder.lockEnabled;
    this.lockThreshold = trimToNull(builder.lockThreshold);
    this.memoryEnabled = builder.memoryEnabled;
    this.memoryInterval = trimToNull(builder.memoryInterval);
    this.memoryTopStats = builder.memoryTopStats;
    this.exceptionEnabled = builder.exceptionEnabled;
    this.exceptionSamplingInterval = trimToNull(builder.exceptionSamplingInterval);
    this.exceptionCollectMessage = builder.exceptionCollectMessage;
    if (startupDelay.isNegative()) {
      throw new IllegalArgumentException("startupDelay must not be negative");
    }
    if (exportInterval.isNegative()) {
      throw new IllegalArgumentException("exportInterval must not be negative");
    }
    if (maxFrames != null && maxFrames.intValue() <= 0) {
      throw new IllegalArgumentException("maxFrames must be positive");
    }
    if (memoryTopStats < 0) {
      throw new IllegalArgumentException("memoryTopStats must not be negative");
    }
  }

  public static Builder builder(Duration startupDelay, String event, String output, Path tempDir) {
    return new Builder(startupDelay, event, output, tempDir);
  }

  public Duration getStartupDelay() {
    return startupDelay;
  }

  public Duration getExportInterval() {
    return exportInterval;
  }

  public String getOutput() {
    return output;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public ProfileExporter getExporter() {
    return exporter;
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  public boolean isExceptionCollectMessage() {
    return exceptionCollectMessage;
  }

  public String resolveOutputPath() {
    String resolved = output.replace("%p", processId());
    return resolved.replace("%t", timestamp());
  }

  public String toStartCommand(String resolvedOutput) {
    StringBuilder builder = new StringBuilder("start");
    if (usesJfrOutput()) {
      builder.append(",jfr");
    }
    String effectiveEvent = effectiveEvent();
    builder.append(",event=").append(effectiveEvent);
    appendIntervalOption(builder, effectiveEvent);
    if (maxFrames != null) {
      builder.append(",jstackdepth=").append(maxFrames);
    }
    if (usesJfrOutput() && memoryEnabled) {
      appendOption(builder, "alloc", defaultIfBlank(memoryInterval, "1m"));
      if (memoryTopStats > 0) {
        builder.append(",live");
      }
    }
    if (usesJfrOutput() && lockEnabled) {
      appendOption(builder, "lock", defaultIfBlank(lockThreshold, defaultLockThreshold()));
    }
    if (exceptionEnabled && usesJfrOutput()) {
      builder.append(",jfrsync=+jdk.JavaExceptionThrow");
      if (exceptionSamplingInterval != null) {
        builder.append("#threshold=").append(exceptionSamplingInterval);
      }
    }
    if (usesJfrOutput()) {
      appendOption(builder, "file", resolvedOutput);
    }
    return builder.toString();
  }

  public String toStopCommand(String resolvedOutput) {
    if (usesJfrOutput()) {
      return "stop";
    }
    return "stop," + outputFormat + ",file=" + resolvedOutput;
  }

  public boolean hasPeriodicExport() {
    return !exportInterval.isZero();
  }

  public boolean usesJfrOutput() {
    return "jfr".equals(outputFormat);
  }

  private static String processId() {
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    int at = runtimeName.indexOf('@');
    if (at > 0) {
      return runtimeName.substring(0, at);
    }
    return runtimeName;
  }

  private static String timestamp() {
    return TIMESTAMP_FORMATTER.format(Instant.now());
  }

  private static void appendOption(StringBuilder builder, String name, String value) {
    if (value != null) {
      builder.append(',').append(name).append('=').append(value);
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String defaultIfBlank(String value, String defaultValue) {
    return value == null ? defaultValue : value;
  }

  private String effectiveEvent() {
    if (!usesJfrOutput()) {
      if (memoryEnabled) {
        return "alloc";
      }
      if (lockEnabled) {
        return "lock";
      }
    }
    return event;
  }

  private void appendIntervalOption(StringBuilder builder, String effectiveEvent) {
    if ("alloc".equals(effectiveEvent)) {
      appendOption(builder, "alloc", defaultIfBlank(memoryInterval, defaultAllocInterval()));
      return;
    }
    if ("lock".equals(effectiveEvent)) {
      appendOption(builder, "lock", defaultIfBlank(lockThreshold, defaultLockThreshold()));
      return;
    }
    appendOption(builder, "interval", sampleInterval);
  }

  private String defaultAllocInterval() {
    return sampleInterval != null ? sampleInterval : "1m";
  }

  private String defaultLockThreshold() {
    return sampleInterval != null ? sampleInterval : "10ms";
  }

  private static void validateNonBlank(String value, String name) {
    requireNonNull(value, name);
    if (value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  public static final class Builder {

    private final Duration startupDelay;
    private final String event;
    private final String output;
    private final Path tempDir;

    private Duration exportInterval = Duration.ZERO;
    private ProfileExporter exporter = ProfileExporter.noop();
    private String outputFormat = "jfr";
    private String sampleInterval;
    private Integer maxFrames;
    private boolean lockEnabled;
    private String lockThreshold;
    private boolean memoryEnabled;
    private String memoryInterval;
    private int memoryTopStats;
    private boolean exceptionEnabled;
    private String exceptionSamplingInterval;
    private boolean exceptionCollectMessage = true;

    private Builder(Duration startupDelay, String event, String output, Path tempDir) {
      this.startupDelay = startupDelay;
      this.event = event;
      this.output = output;
      this.tempDir = tempDir;
    }

    @CanIgnoreReturnValue
    public Builder exportInterval(Duration exportInterval) {
      this.exportInterval = exportInterval;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exporter(ProfileExporter exporter) {
      this.exporter = exporter;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder outputFormat(String outputFormat) {
      this.outputFormat = outputFormat;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder sampleInterval(String sampleInterval) {
      this.sampleInterval = sampleInterval;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder maxFrames(Integer maxFrames) {
      this.maxFrames = maxFrames;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder lockEnabled(boolean lockEnabled) {
      this.lockEnabled = lockEnabled;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder lockThreshold(String lockThreshold) {
      this.lockThreshold = lockThreshold;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder memoryEnabled(boolean memoryEnabled) {
      this.memoryEnabled = memoryEnabled;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder memoryInterval(String memoryInterval) {
      this.memoryInterval = memoryInterval;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder memoryTopStats(int memoryTopStats) {
      this.memoryTopStats = memoryTopStats;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exceptionEnabled(boolean exceptionEnabled) {
      this.exceptionEnabled = exceptionEnabled;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exceptionSamplingInterval(String exceptionSamplingInterval) {
      this.exceptionSamplingInterval = exceptionSamplingInterval;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder exceptionCollectMessage(boolean exceptionCollectMessage) {
      this.exceptionCollectMessage = exceptionCollectMessage;
      return this;
    }

    public AsyncProfilerConfig build() {
      return new AsyncProfilerConfig(this);
    }
  }
}
