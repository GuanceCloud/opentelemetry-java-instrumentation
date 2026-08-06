/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import io.opentelemetry.instrumentation.asyncprofiler.AsyncProfilerConfig;
import io.opentelemetry.instrumentation.asyncprofiler.ProfileExporter;
import io.opentelemetry.instrumentation.asyncprofiler.internal.BinaryHttpProfileExporter;
import io.opentelemetry.instrumentation.asyncprofiler.internal.DatakitProfileExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

final class AgentAsyncProfilerConfiguration {

  private static final Logger logger =
      Logger.getLogger(AgentAsyncProfilerConfiguration.class.getName());
  private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofSeconds(60);

  private static final String ENABLED = "otel.profiling.enabled";
  private static final String EXPORTER = "otel.profiling.exporter";
  private static final String ENDPOINT = "otel.profiling.endpoint";
  private static final String DATAKIT_TIMEOUT = "otel.profiling.datakit.timeout";
  private static final String SAMPLE_INTERVAL = "otel.profiling.sample-interval";
  private static final String EXPORT_INTERVAL = "otel.profiling.export-interval";
  private static final String MAX_FRAMES = "otel.profiling.max-frames";
  private static final String EXCEPTION_ENABLED = "otel.profiling.exception.enabled";
  private static final String EXCEPTION_SAMPLING_INTERVAL =
      "otel.profiling.exception.sampling-interval";
  private static final String EXCEPTION_COLLECT_MESSAGE =
      "otel.profiling.exception.collect-message";
  private static final String LOCK_ENABLED = "otel.profiling.lock.enabled";
  private static final String MEMORY_ENABLED = "otel.profiling.memory.enabled";
  private static final String MEMORY_INTERVAL = "otel.profiling.memory.interval";
  private static final String MEMORY_TOP_STATS = "otel.profiling.memory.top-stats";
  private static final String PPROF_PATH = "otel.profiling.pprof.path";
  private static final String PPROF_UPLOAD_URL = "otel.profiling.pprof.upload-url";
  private static final String PPROF_HEADERS = "otel.profiling.pprof.headers";
  private static final String EVENT = "otel.profiling.async.event";
  private static final String STARTUP_DELAY = "otel.profiling.async.startup-delay";
  private static final String OUTPUT = "otel.profiling.async.output";
  private static final String TEMP_DIR = "otel.profiling.async.temp-dir";
  private static final String OTLP_HEADERS = "otel.exporter.otlp.headers";
  private static final String OTLP_TIMEOUT = "otel.exporter.otlp.timeout";
  private static final String OTLP_PROTOCOL = "otel.exporter.otlp.profiles.protocol";
  private static final String OTLP_ENDPOINT = "otel.exporter.otlp.profiles.endpoint";
  private static final String OTLP_SIGNAL_TIMEOUT = "otel.exporter.otlp.profiles.timeout";
  private static final String OTLP_SIGNAL_HEADERS = "otel.exporter.otlp.profiles.headers";

  private AgentAsyncProfilerConfiguration() {}

  static boolean isEnabled(ConfigProperties config) {
    return Boolean.TRUE.equals(config.getBoolean(ENABLED));
  }

  static AsyncProfilerConfig create(ConfigProperties config, Resource resource) {
    String exporterName = normalizeExporter(defaultIfBlank(config.getString(EXPORTER), "file"));
    String outputFormat = outputFormat(exporterName);
    String output = resolveOutput(config, exporterName);
    warnUnsupportedCompatibilityModes(config, exporterName, outputFormat);
    return AsyncProfilerConfig.builder(
            defaultIfNull(config.getDuration(STARTUP_DELAY), Duration.ZERO),
            defaultIfBlank(config.getString(EVENT), "cpu"),
            output,
            Paths.get(defaultIfBlank(config.getString(TEMP_DIR), defaultTempDir())))
        .exportInterval(defaultIfNull(config.getDuration(EXPORT_INTERVAL), DEFAULT_EXPORT_INTERVAL))
        .exporter(createExporter(config, resource, exporterName))
        .outputFormat(outputFormat)
        .sampleInterval(blankToNull(config.getString(SAMPLE_INTERVAL)))
        .maxFrames(config.getInt(MAX_FRAMES))
        .lockEnabled(defaultBoolean(config.getBoolean(LOCK_ENABLED), true))
        .lockThreshold(blankToNull(config.getString(SAMPLE_INTERVAL)))
        .memoryEnabled(defaultBoolean(config.getBoolean(MEMORY_ENABLED), true))
        .memoryInterval(blankToNull(config.getString(MEMORY_INTERVAL)))
        .memoryTopStats(defaultInt(config.getInt(MEMORY_TOP_STATS), 0))
        .exceptionEnabled(defaultBoolean(config.getBoolean(EXCEPTION_ENABLED), false))
        .exceptionSamplingInterval(blankToNull(config.getString(EXCEPTION_SAMPLING_INTERVAL)))
        .exceptionCollectMessage(defaultBoolean(config.getBoolean(EXCEPTION_COLLECT_MESSAGE), true))
        .build();
  }

  private static ProfileExporter createExporter(
      ConfigProperties config, Resource resource, String exporterName) {
    if ("datakit".equals(exporterName)) {
      return new DatakitProfileExporter(
          toUrl(
              defaultIfBlank(
                  config.getString(ENDPOINT), "http://localhost:9529/profiling/v1/input")),
          defaultIfNull(config.getDuration(DATAKIT_TIMEOUT), Duration.ofSeconds(10)),
          DatakitProfilingTags.create(resource));
    }
    if ("otlp".equals(exporterName)) {
      if (!isSupportedOtlpProtocol(config)) {
        logger.warning("OTLP profiling exporter currently supports http/protobuf only");
        return ProfileExporter.noop();
      }
      return new BinaryHttpProfileExporter(
          toUrl(
              defaultIfBlank(config.getString(OTLP_ENDPOINT), "http://localhost:4318/v1/profiles")),
          defaultIfNull(
              config.getDuration(OTLP_SIGNAL_TIMEOUT),
              defaultIfNull(config.getDuration(OTLP_TIMEOUT), Duration.ofSeconds(10))),
          "application/x-protobuf",
          parseHeaders(config.getString(OTLP_HEADERS), config.getString(OTLP_SIGNAL_HEADERS)));
    }
    if ("pprof".equals(exporterName)) {
      String uploadUrl = blankToNull(config.getString(PPROF_UPLOAD_URL));
      if (uploadUrl == null) {
        return ProfileExporter.noop();
      }
      logger.warning(
          "PPROF exporter compatibility mode uses async-profiler OTLP payloads because async-profiler 4.4 has no native pprof output");
      return new BinaryHttpProfileExporter(
          toUrl(uploadUrl),
          Duration.ofSeconds(10),
          "application/x-protobuf",
          parseHeaders(null, config.getString(PPROF_HEADERS)));
    }
    return ProfileExporter.noop();
  }

  private static Duration defaultIfNull(Duration value, Duration defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static boolean defaultBoolean(Boolean value, boolean defaultValue) {
    return value != null ? value.booleanValue() : defaultValue;
  }

  private static int defaultInt(Integer value, int defaultValue) {
    return value != null ? value.intValue() : defaultValue;
  }

  private static String defaultIfBlank(String value, String defaultValue) {
    return value == null || value.trim().isEmpty() ? defaultValue : value;
  }

  private static String defaultOutput() {
    return Paths.get(
            System.getProperty("java.io.tmpdir"), "opentelemetry", "profiles", "profile-%p.jfr")
        .toString();
  }

  private static String defaultTempDir() {
    Path path = Paths.get(System.getProperty("java.io.tmpdir"), "opentelemetry", "async-profiler");
    return path.toString();
  }

  private static String normalizeExporter(String exporter) {
    return exporter.trim().toLowerCase(Locale.ROOT);
  }

  private static URL toUrl(String value) {
    try {
      return new URL(value);
    } catch (MalformedURLException exception) {
      throw new IllegalArgumentException("Invalid profiling endpoint: " + value, exception);
    }
  }

  private static String outputFormat(String exporterName) {
    if ("otlp".equals(exporterName) || "pprof".equals(exporterName)) {
      return "otlp";
    }
    return "jfr";
  }

  private static String resolveOutput(ConfigProperties config, String exporterName) {
    if ("pprof".equals(exporterName)) {
      return defaultIfBlank(config.getString(PPROF_PATH), defaultPprofPath());
    }
    if ("otlp".equals(exporterName)) {
      return defaultOtlpPath();
    }
    return defaultIfBlank(config.getString(OUTPUT), defaultOutput());
  }

  private static String defaultOtlpPath() {
    return Paths.get(
            System.getProperty("java.io.tmpdir"), "opentelemetry", "profiles", "profile-%p.otlp")
        .toString();
  }

  private static String defaultPprofPath() {
    return Paths.get(
            System.getProperty("java.io.tmpdir"), "opentelemetry", "profiles", "profile-%p.pprof")
        .toString();
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean isSupportedOtlpProtocol(ConfigProperties config) {
    String protocol = defaultIfBlank(config.getString(OTLP_PROTOCOL), "http/protobuf");
    return "http/protobuf".equals(protocol);
  }

  private static Map<String, String> parseHeaders(String sharedHeaders, String signalHeaders) {
    Map<String, String> headers = new LinkedHashMap<>();
    appendHeaders(headers, sharedHeaders);
    appendHeaders(headers, signalHeaders);
    return headers;
  }

  private static void appendHeaders(Map<String, String> headers, String rawHeaders) {
    String value = blankToNull(rawHeaders);
    if (value == null) {
      return;
    }
    for (String entry : value.split(",")) {
      int separator = entry.indexOf('=');
      if (separator <= 0 || separator == entry.length() - 1) {
        continue;
      }
      headers.put(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
    }
  }

  private static void warnUnsupportedCompatibilityModes(
      ConfigProperties config, String exporterName, String outputFormat) {
    if ("pprof".equals(exporterName)) {
      logger.warning(
          "PPROF exporter compatibility mode emits OTLP profile payloads because async-profiler 4.4 does not support native pprof output");
    }
    if (!"jfr".equals(outputFormat)) {
      boolean memoryEnabled = defaultBoolean(config.getBoolean(MEMORY_ENABLED), true);
      boolean lockEnabled = defaultBoolean(config.getBoolean(LOCK_ENABLED), true);
      if (memoryEnabled && lockEnabled) {
        logger.warning(
            "Non-JFR profiling output supports only one async-profiler event; memory profiling takes precedence over lock profiling");
      }
      if (memoryEnabled) {
        logger.warning(
            "Non-JFR profiling output cannot multiplex events; OTEL_PROFILING_MEMORY_ENABLED switches the primary async-profiler event to alloc");
      } else if (lockEnabled) {
        logger.warning(
            "Non-JFR profiling output cannot multiplex events; OTEL_PROFILING_LOCK_ENABLED switches the primary async-profiler event to lock");
      }
      if (defaultInt(config.getInt(MEMORY_TOP_STATS), 0) > 0) {
        logger.warning(
            "OTEL_PROFILING_MEMORY_TOP_STATS currently maps to async-profiler live allocation stats for JFR output only; ignoring it for non-JFR output");
      }
    }
    if (defaultBoolean(config.getBoolean(EXCEPTION_ENABLED), false)
        && !"jfr".equals(outputFormat)) {
      logger.warning(
          "Exception profiling currently requires JFR output; ignoring exception settings");
    }
    if (!defaultBoolean(config.getBoolean(EXCEPTION_COLLECT_MESSAGE), true)) {
      logger.warning(
          "async-profiler JFR exception events always include exception messages; OTEL_PROFILING_EXCEPTION_COLLECT_MESSAGE=false is ignored");
    }
  }
}
