/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class MetaConfiguration {

  private static final String META_EXPORTER = "otel.meta.exporter";
  private static final String ENDPOINT = "otel.exporter.meta.endpoint";
  private static final String HEADERS = "otel.exporter.meta.headers";
  private static final String TIMEOUT = "otel.exporter.meta.timeout";
  private static final String COMPRESSION = "otel.exporter.meta.compression";
  private static final String MAX_RETRIES = "otel.exporter.meta.max-retries";
  private static final String MAX_REQUEST_SIZE = "otel.exporter.meta.max-request-size";
  private static final String SCHEDULE_DELAY = "otel.meta.batch.schedule-delay";
  private static final String SHUTDOWN_TIMEOUT = "otel.meta.shutdown-timeout";
  private static final String LEGACY_EXPORT_TIMEOUT = "otel.meta.batch.export-timeout";
  private static final String HEARTBEAT_INTERVAL = "otel.meta.heartbeat.interval";
  private static final String EXTENDED_HEARTBEAT_INTERVAL = "otel.meta.extended-heartbeat.interval";
  private static final String APP_STARTED_ENABLED = "otel.meta.app-started.enabled";
  private static final String APP_DEPENDENCIES_LOADED_ENABLED =
      "otel.meta.app-dependencies-loaded.enabled";
  private static final String APP_INTEGRATIONS_CHANGE_ENABLED =
      "otel.meta.app-integrations-change.enabled";

  private static final int DEFAULT_TIMEOUT_MILLIS = 10_000;
  private static final int DEFAULT_MAX_RETRIES = 3;
  private static final int DEFAULT_MAX_REQUEST_SIZE_BYTES = 5 * 1024 * 1024;
  private static final long DEFAULT_SCHEDULE_DELAY_MILLIS = 1_000;
  private static final long DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 10_000;
  private static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = 60_000;
  private static final long DEFAULT_EXTENDED_HEARTBEAT_INTERVAL_MILLIS = 86_400_000;
  private static final Set<String> RESERVED_HEADERS =
      new HashSet<>(
          asList(
              "connection",
              "content-encoding",
              "content-length",
              "content-type",
              "host",
              "transfer-encoding"));

  private final boolean enabled;
  @Nullable private final URL endpoint;
  private final Map<String, String> headers;
  private final int timeoutMillis;
  private final boolean gzipEnabled;
  private final int maxRetries;
  private final int maxRequestSizeBytes;
  private final long scheduleDelayMillis;
  private final long shutdownTimeoutMillis;
  private final long heartbeatIntervalMillis;
  private final long extendedHeartbeatIntervalMillis;
  private final boolean appStartedEnabled;
  private final boolean appDependenciesLoadedEnabled;
  private final boolean appIntegrationsChangeEnabled;

  static MetaConfiguration create(ConfigProperties config) {
    String exporter = normalize(config.getString(META_EXPORTER), "none");
    if ("none".equals(exporter)) {
      return disabled();
    }
    if (!"meta".equals(exporter)) {
      throw new IllegalArgumentException(
          "Unsupported value for " + META_EXPORTER + ": " + exporter);
    }

    URL endpoint = parseEndpoint(config.getString(ENDPOINT));
    int timeoutMillis = positive(config.getLong(TIMEOUT), DEFAULT_TIMEOUT_MILLIS, TIMEOUT);
    String compression = normalize(config.getString(COMPRESSION), "none");
    if (!"none".equals(compression) && !"gzip".equals(compression)) {
      throw new IllegalArgumentException(
          "Unsupported value for " + COMPRESSION + ": " + compression);
    }
    int maxRetries = nonNegative(config.getInt(MAX_RETRIES), DEFAULT_MAX_RETRIES, MAX_RETRIES);
    int maxRequestSizeBytes =
        positive(
            config.getLong(MAX_REQUEST_SIZE), DEFAULT_MAX_REQUEST_SIZE_BYTES, MAX_REQUEST_SIZE);
    long scheduleDelayMillis =
        positive(config.getLong(SCHEDULE_DELAY), DEFAULT_SCHEDULE_DELAY_MILLIS, SCHEDULE_DELAY);
    Long configuredShutdownTimeout = config.getLong(SHUTDOWN_TIMEOUT);
    String shutdownTimeoutProperty = SHUTDOWN_TIMEOUT;
    if (configuredShutdownTimeout == null) {
      configuredShutdownTimeout = config.getLong(LEGACY_EXPORT_TIMEOUT);
      if (configuredShutdownTimeout != null) {
        shutdownTimeoutProperty = LEGACY_EXPORT_TIMEOUT;
      }
    }
    long shutdownTimeoutMillis =
        positive(
            configuredShutdownTimeout, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS, shutdownTimeoutProperty);
    long heartbeatIntervalMillis =
        positive(
            config.getLong(HEARTBEAT_INTERVAL),
            DEFAULT_HEARTBEAT_INTERVAL_MILLIS,
            HEARTBEAT_INTERVAL);
    long extendedHeartbeatIntervalMillis =
        positive(
            config.getLong(EXTENDED_HEARTBEAT_INTERVAL),
            DEFAULT_EXTENDED_HEARTBEAT_INTERVAL_MILLIS,
            EXTENDED_HEARTBEAT_INTERVAL);

    return new MetaConfiguration(
        true,
        endpoint,
        sanitizeHeaders(config.getMap(HEADERS)),
        timeoutMillis,
        "gzip".equals(compression),
        maxRetries,
        maxRequestSizeBytes,
        scheduleDelayMillis,
        shutdownTimeoutMillis,
        heartbeatIntervalMillis,
        extendedHeartbeatIntervalMillis,
        defaultBoolean(config.getBoolean(APP_STARTED_ENABLED), true),
        defaultBoolean(config.getBoolean(APP_DEPENDENCIES_LOADED_ENABLED), true),
        defaultBoolean(config.getBoolean(APP_INTEGRATIONS_CHANGE_ENABLED), true));
  }

  private static MetaConfiguration disabled() {
    return new MetaConfiguration(
        false,
        null,
        emptyMap(),
        DEFAULT_TIMEOUT_MILLIS,
        false,
        DEFAULT_MAX_RETRIES,
        DEFAULT_MAX_REQUEST_SIZE_BYTES,
        DEFAULT_SCHEDULE_DELAY_MILLIS,
        DEFAULT_SHUTDOWN_TIMEOUT_MILLIS,
        DEFAULT_HEARTBEAT_INTERVAL_MILLIS,
        DEFAULT_EXTENDED_HEARTBEAT_INTERVAL_MILLIS,
        true,
        true,
        true);
  }

  private MetaConfiguration(
      boolean enabled,
      @Nullable URL endpoint,
      Map<String, String> headers,
      int timeoutMillis,
      boolean gzipEnabled,
      int maxRetries,
      int maxRequestSizeBytes,
      long scheduleDelayMillis,
      long shutdownTimeoutMillis,
      long heartbeatIntervalMillis,
      long extendedHeartbeatIntervalMillis,
      boolean appStartedEnabled,
      boolean appDependenciesLoadedEnabled,
      boolean appIntegrationsChangeEnabled) {
    this.enabled = enabled;
    this.endpoint = endpoint;
    this.headers = headers;
    this.timeoutMillis = timeoutMillis;
    this.gzipEnabled = gzipEnabled;
    this.maxRetries = maxRetries;
    this.maxRequestSizeBytes = maxRequestSizeBytes;
    this.scheduleDelayMillis = scheduleDelayMillis;
    this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    this.heartbeatIntervalMillis = heartbeatIntervalMillis;
    this.extendedHeartbeatIntervalMillis = extendedHeartbeatIntervalMillis;
    this.appStartedEnabled = appStartedEnabled;
    this.appDependenciesLoadedEnabled = appDependenciesLoadedEnabled;
    this.appIntegrationsChangeEnabled = appIntegrationsChangeEnabled;
  }

  boolean isEnabled() {
    return enabled;
  }

  URL getEndpoint() {
    if (endpoint == null) {
      throw new IllegalStateException("Meta endpoint is unavailable when the exporter is disabled");
    }
    return endpoint;
  }

  Map<String, String> getHeaders() {
    return headers;
  }

  int getTimeoutMillis() {
    return timeoutMillis;
  }

  boolean isGzipEnabled() {
    return gzipEnabled;
  }

  int getMaxRetries() {
    return maxRetries;
  }

  int getMaxRequestSizeBytes() {
    return maxRequestSizeBytes;
  }

  long getScheduleDelayMillis() {
    return scheduleDelayMillis;
  }

  long getShutdownTimeoutMillis() {
    return shutdownTimeoutMillis;
  }

  long getHeartbeatIntervalMillis() {
    return heartbeatIntervalMillis;
  }

  long getExtendedHeartbeatIntervalMillis() {
    return extendedHeartbeatIntervalMillis;
  }

  boolean isAppStartedEnabled() {
    return appStartedEnabled;
  }

  boolean isAppDependenciesLoadedEnabled() {
    return appDependenciesLoadedEnabled;
  }

  boolean isAppIntegrationsChangeEnabled() {
    return appIntegrationsChangeEnabled;
  }

  private static URL parseEndpoint(@Nullable String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(
          ENDPOINT + " must be configured when " + META_EXPORTER + " is meta");
    }
    try {
      URL endpoint = new URL(value.trim());
      String protocol = endpoint.getProtocol();
      if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
        throw new IllegalArgumentException(ENDPOINT + " must use http or https");
      }
      if (endpoint.getHost().isEmpty()) {
        throw new IllegalArgumentException(ENDPOINT + " must include a host");
      }
      if (endpoint.getUserInfo() != null) {
        throw new IllegalArgumentException(ENDPOINT + " must not include user info");
      }
      if (endpoint.getRef() != null) {
        throw new IllegalArgumentException(ENDPOINT + " must not include a fragment");
      }
      return endpoint;
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid " + ENDPOINT, e);
    }
  }

  private static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
    if (headers.isEmpty()) {
      return emptyMap();
    }
    Map<String, String> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      String name = decodeHeader(entry.getKey());
      String value = decodeHeader(entry.getValue());
      if (name == null || value == null) {
        throw new IllegalArgumentException(HEADERS + " must not contain null names or values");
      }
      name = name.trim();
      if (!isValidHeaderName(name)) {
        throw new IllegalArgumentException("Invalid HTTP header name in " + HEADERS + ": " + name);
      }
      if (RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(HEADERS + " must not override reserved header " + name);
      }
      if (!isValidHeaderValue(value)) {
        throw new IllegalArgumentException(
            "Invalid HTTP header value in " + HEADERS + " for " + name);
      }
      sanitized.put(name, value);
    }
    return unmodifiableMap(sanitized);
  }

  private static boolean isValidHeaderName(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      if ((character >= 'a' && character <= 'z')
          || (character >= 'A' && character <= 'Z')
          || (character >= '0' && character <= '9')) {
        continue;
      }
      if ("!#$%&'*+-.^_`|~".indexOf(character) < 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidHeaderValue(String value) {
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      if ((character < 0x20 && character != '\t') || character == 0x7f) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static String decodeHeader(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, UTF_8.name());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid URL encoding in " + HEADERS, e);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private static String normalize(@Nullable String value, String defaultValue) {
    return value == null || value.trim().isEmpty()
        ? defaultValue
        : value.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean defaultBoolean(@Nullable Boolean value, boolean defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static int positive(@Nullable Long value, int defaultValue, String propertyName) {
    long resolved = value != null ? value : defaultValue;
    if (resolved <= 0 || resolved > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          propertyName + " must be between 1 and " + Integer.MAX_VALUE);
    }
    return (int) resolved;
  }

  private static long positive(@Nullable Long value, long defaultValue, String propertyName) {
    long resolved = value != null ? value : defaultValue;
    if (resolved <= 0) {
      throw new IllegalArgumentException(propertyName + " must be positive");
    }
    return resolved;
  }

  private static int nonNegative(@Nullable Integer value, int defaultValue, String propertyName) {
    int resolved = value != null ? value : defaultValue;
    if (resolved < 0) {
      throw new IllegalArgumentException(propertyName + " must not be negative");
    }
    return resolved;
  }
}
