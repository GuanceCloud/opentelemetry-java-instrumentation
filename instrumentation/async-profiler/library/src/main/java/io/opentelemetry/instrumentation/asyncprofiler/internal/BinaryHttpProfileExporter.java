/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.asyncprofiler.ProfileArtifact;
import io.opentelemetry.instrumentation.asyncprofiler.ProfileExporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uploads generated profile artifacts as raw binary payloads over HTTP.
 *
 * <p>This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class BinaryHttpProfileExporter implements ProfileExporter {

  private static final int COPY_BUFFER_SIZE = 8192;

  private final URL endpoint;
  private final int timeoutMillis;
  private final String contentType;
  private final Map<String, String> headers;

  public BinaryHttpProfileExporter(
      URL endpoint, Duration timeout, String contentType, Map<String, String> headers) {
    this.endpoint = requireNonNull(endpoint, "endpoint");
    this.timeoutMillis = toTimeoutMillis(requireNonNull(timeout, "timeout"));
    this.contentType = requireNonNull(contentType, "contentType");
    this.headers = sanitizeHeaders(headers);
  }

  @Override
  public void export(ProfileArtifact artifact) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    connection.setConnectTimeout(timeoutMillis);
    connection.setReadTimeout(timeoutMillis);
    connection.setRequestProperty("Content-Type", contentType);
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      connection.setRequestProperty(entry.getKey(), entry.getValue());
    }

    try {
      connection.setFixedLengthStreamingMode(Files.size(artifact.getPath()));
      try (OutputStream outputStream = connection.getOutputStream();
          InputStream inputStream = Files.newInputStream(artifact.getPath())) {
        copy(inputStream, outputStream);
      }

      int responseCode = connection.getResponseCode();
      if (responseCode / 100 != 2) {
        throw new IOException(
            "Profile upload failed with status "
                + responseCode
                + " and body: "
                + readResponseBody(connection));
      }

      try (InputStream inputStream = connection.getInputStream()) {
        drain(inputStream);
      }
    } finally {
      connection.disconnect();
    }
  }

  private static int toTimeoutMillis(Duration timeout) {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    long timeoutMillis = timeout.toMillis();
    if (timeoutMillis > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) timeoutMillis;
  }

  private static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
    Map<String, String> sanitized = new LinkedHashMap<>();
    if (headers == null) {
      return sanitized;
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
        continue;
      }
      sanitized.put(key, value);
    }
    return sanitized;
  }

  private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      outputStream.write(buffer, 0, read);
    }
  }

  private static void drain(InputStream inputStream) throws IOException {
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    while (inputStream.read(buffer) >= 0) {
      // Drain response body.
    }
  }

  private static String readResponseBody(HttpURLConnection connection) throws IOException {
    InputStream stream = connection.getErrorStream();
    if (stream == null) {
      stream = connection.getInputStream();
    }
    if (stream == null) {
      return "";
    }
    try (InputStream inputStream = stream;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      copy(inputStream, outputStream);
      return outputStream.toString("UTF-8");
    }
  }
}
