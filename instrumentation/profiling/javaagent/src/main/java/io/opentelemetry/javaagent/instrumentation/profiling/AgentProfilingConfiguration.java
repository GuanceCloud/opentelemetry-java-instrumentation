/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.profiling;

import static java.util.Locale.ROOT;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

final class AgentProfilingConfiguration {

  private static final String PREFIX = "otel.profiling.";
  private static final String LEGACY_PREFIX = "otel.instrumentation.profiling.";

  private final Duration interval;
  private final Duration startupDelay;
  private final Duration maxAge;
  private final long maxSize;
  private final int stackDepth;
  private final Path tempDir;
  private final String exporter;
  private final boolean memoryEnabled;
  private final Boolean memoryAllocationSampling;
  private final Boolean memoryOldObjectSampling;
  private final Path fileExportPath;
  private final URL datakitEndpoint;
  private final Duration datakitTimeout;

  static boolean isEnabled(ConfigProperties config) {
    return defaultBoolean(getBoolean(config, "enabled"), false);
  }

  static AgentProfilingConfiguration create(ConfigProperties config) {
    String exporter = normalizeExporter(defaultString(getString(config, "exporter"), "none"));
    return new AgentProfilingConfiguration(
        defaultDuration(getDuration(config, "interval"), Duration.ofMinutes(1)),
        defaultDuration(getDuration(config, "startup-delay"), Duration.ZERO),
        defaultDuration(getDuration(config, "max-age"), Duration.ofMinutes(5)),
        defaultLong(getLong(config, "max-size"), 0),
        defaultInt(getInt(config, "stack-depth"), 64),
        toPath(getString(config, "temp-dir")),
        exporter,
        defaultBoolean(getBoolean(config, "memory.enabled"), false),
        getBoolean(config, "memory.allocation-sampling"),
        getBoolean(config, "memory.old-object-sampling"),
        toPathOrDefault(
            getString(config, "experimental.file-export.path"),
            Paths.get(System.getProperty("java.io.tmpdir"), "otel-profiles")),
        toUrlOrDefault(
            getString(
                config,
                new String[] {"endpoint", "datakit.endpoint"},
                "OTEL_PROFILING_ENDPOINT",
                "OTEL_PROFILING_DATAKIT_ENDPOINT"),
            "http://localhost:9529/profiling/v1/input"),
        defaultDuration(getDuration(config, "datakit.timeout"), Duration.ofSeconds(10)));
  }

  private AgentProfilingConfiguration(
      Duration interval,
      Duration startupDelay,
      Duration maxAge,
      long maxSize,
      int stackDepth,
      Path tempDir,
      String exporter,
      boolean memoryEnabled,
      Boolean memoryAllocationSampling,
      Boolean memoryOldObjectSampling,
      Path fileExportPath,
      URL datakitEndpoint,
      Duration datakitTimeout) {
    this.interval = interval;
    this.startupDelay = startupDelay;
    this.maxAge = maxAge;
    this.maxSize = maxSize;
    this.stackDepth = stackDepth;
    this.tempDir = tempDir;
    this.exporter = exporter;
    this.memoryEnabled = memoryEnabled;
    this.memoryAllocationSampling = memoryAllocationSampling;
    this.memoryOldObjectSampling = memoryOldObjectSampling;
    this.fileExportPath = fileExportPath;
    this.datakitEndpoint = datakitEndpoint;
    this.datakitTimeout = datakitTimeout;
  }

  Duration getInterval() {
    return interval;
  }

  Duration getStartupDelay() {
    return startupDelay;
  }

  Duration getMaxAge() {
    return maxAge;
  }

  long getMaxSize() {
    return maxSize;
  }

  int getStackDepth() {
    return stackDepth;
  }

  Path getTempDir() {
    return tempDir;
  }

  String getExporter() {
    return exporter;
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

  Path getFileExportPath() {
    return fileExportPath;
  }

  URL getDatakitEndpoint() {
    return datakitEndpoint;
  }

  Duration getDatakitTimeout() {
    return datakitTimeout;
  }

  private static Duration defaultDuration(Duration value, Duration defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static boolean defaultBoolean(Boolean value, boolean defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static long defaultLong(Long value, long defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static int defaultInt(Integer value, int defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static String defaultString(String value, String defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static String normalizeExporter(String exporter) {
    String normalized = exporter.trim().toLowerCase(ROOT);
    return normalized.isEmpty() ? "none" : normalized;
  }

  private static Boolean getBoolean(ConfigProperties config, String suffix) {
    Boolean value = config.getBoolean(PREFIX + suffix);
    return value != null ? value : config.getBoolean(LEGACY_PREFIX + suffix);
  }

  private static Duration getDuration(ConfigProperties config, String suffix) {
    Duration value = config.getDuration(PREFIX + suffix);
    return value != null ? value : config.getDuration(LEGACY_PREFIX + suffix);
  }

  private static Integer getInt(ConfigProperties config, String suffix) {
    Integer value = config.getInt(PREFIX + suffix);
    return value != null ? value : config.getInt(LEGACY_PREFIX + suffix);
  }

  private static Long getLong(ConfigProperties config, String suffix) {
    Long value = config.getLong(PREFIX + suffix);
    return value != null ? value : config.getLong(LEGACY_PREFIX + suffix);
  }

  private static String getString(ConfigProperties config, String suffix) {
    String value = config.getString(PREFIX + suffix);
    return value != null ? value : config.getString(LEGACY_PREFIX + suffix);
  }

  private static String getString(
      ConfigProperties config, String[] suffixes, String... envAliases) {
    for (String suffix : suffixes) {
      String value = getString(config, suffix);
      if (value != null) {
        return value;
      }
    }
    return getString((String) null, envAliases);
  }

  private static String getString(String value, String... envAliases) {
    if (value != null) {
      return value;
    }
    for (String envAlias : envAliases) {
      value = System.getenv(envAlias);
      if (value != null && !value.isEmpty()) {
        return value;
      }
    }
    return null;
  }

  private static Path toPath(String value) {
    return value == null || value.isEmpty() ? null : Paths.get(value);
  }

  private static Path toPathOrDefault(String value, Path defaultValue) {
    return value == null || value.isEmpty() ? defaultValue : Paths.get(value);
  }

  private static URL toUrlOrDefault(String value, String defaultValue) {
    try {
      return new URL(value == null || value.isEmpty() ? defaultValue : value);
    } catch (MalformedURLException exception) {
      throw new IllegalArgumentException("Invalid profiling Datakit endpoint", exception);
    }
  }
}
