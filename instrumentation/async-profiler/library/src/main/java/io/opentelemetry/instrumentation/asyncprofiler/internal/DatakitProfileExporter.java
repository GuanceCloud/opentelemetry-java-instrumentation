/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Uploads async-profiler JFR files to Datakit using multipart form data.
 *
 * <p>This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class DatakitProfileExporter implements ProfileExporter {

  private static final int COPY_BUFFER_SIZE = 8192;
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  private final URL endpoint;
  private final int timeoutMillis;
  private final Map<String, String> tagsProfiler;

  public DatakitProfileExporter(URL endpoint, Duration timeout, Map<String, String> tagsProfiler) {
    this.endpoint = requireNonNull(endpoint, "endpoint");
    this.timeoutMillis = toTimeoutMillis(requireNonNull(timeout, "timeout"));
    this.tagsProfiler = sanitizeTags(tagsProfiler);
  }

  @Override
  public void export(ProfileArtifact artifact) throws IOException {
    String boundary = "----otel-async-profile-" + UUID.randomUUID();
    HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    connection.setConnectTimeout(timeoutMillis);
    connection.setReadTimeout(timeoutMillis);
    connection.setChunkedStreamingMode(COPY_BUFFER_SIZE);
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

    try {
      try (OutputStream outputStream = connection.getOutputStream()) {
        writeMainPart(outputStream, boundary, artifact);
        writeEventPart(outputStream, boundary, artifact);
        writeAscii(outputStream, "--" + boundary + "--\r\n");
      }

      int responseCode = connection.getResponseCode();
      if (responseCode / 100 != 2) {
        throw new IOException(
            "Datakit profile upload failed with status "
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

  private static void writeMainPart(
      OutputStream outputStream, String boundary, ProfileArtifact artifact) throws IOException {
    writeAscii(outputStream, "--" + boundary + "\r\n");
    writeAscii(
        outputStream,
        "Content-Disposition: form-data; name=\"main\"; filename=\"main."
            + artifact.getFormat()
            + "\"\r\n");
    writeAscii(outputStream, "Content-Type: application/octet-stream\r\n\r\n");
    try (InputStream inputStream = Files.newInputStream(artifact.getPath())) {
      copy(inputStream, outputStream);
    }
    writeAscii(outputStream, "\r\n");
  }

  private void writeEventPart(OutputStream outputStream, String boundary, ProfileArtifact artifact)
      throws IOException {
    byte[] eventJson = createEventJson(artifact).getBytes(UTF_8);
    writeAscii(outputStream, "--" + boundary + "\r\n");
    writeAscii(
        outputStream,
        "Content-Disposition: form-data; name=\"event\"; filename=\"event.json\"\r\n");
    writeAscii(outputStream, "Content-Type: application/json\r\n\r\n");
    outputStream.write(eventJson);
    writeAscii(outputStream, "\r\n");
  }

  private String createEventJson(ProfileArtifact artifact) {
    StringBuilder builder = new StringBuilder(256);
    builder.append('{');
    appendJsonField(builder, "attachments", "[\"main." + jsonEscape(artifact.getFormat()) + "\"]");
    appendJsonField(builder, "tags_profiler", jsonString(joinTags(tagsProfiler)));
    appendJsonField(builder, "start", jsonString(TIME_FORMATTER.format(artifact.getStartTime())));
    appendJsonField(builder, "end", jsonString(TIME_FORMATTER.format(artifact.getEndTime())));
    appendJsonField(builder, "family", jsonString("java"));
    appendJsonField(builder, "language", jsonString("java"));
    appendJsonField(builder, "format", jsonString(artifact.getFormat()));
    builder.append('}');
    return builder.toString();
  }

  private static void appendJsonField(StringBuilder builder, String name, String value) {
    if (builder.length() > 1) {
      builder.append(',');
    }
    builder.append(jsonString(name)).append(':').append(value);
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

  private static Map<String, String> sanitizeTags(Map<String, String> tagsProfiler) {
    Map<String, String> sanitized = new LinkedHashMap<>();
    if (tagsProfiler == null) {
      return sanitized;
    }
    for (Map.Entry<String, String> entry : tagsProfiler.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
        continue;
      }
      sanitized.put(key, sanitizeTagValue(value));
    }
    return sanitized;
  }

  private static String joinTags(Map<String, String> tags) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      if (builder.length() > 0) {
        builder.append(',');
      }
      builder.append(entry.getKey()).append(':').append(entry.getValue());
    }
    return builder.toString();
  }

  private static String sanitizeTagValue(String value) {
    return value.replace('\r', ' ').replace('\n', ' ').replace(',', '_');
  }

  private static String jsonString(String value) {
    return "\"" + jsonEscape(value) + "\"";
  }

  private static String jsonEscape(String value) {
    StringBuilder builder = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      switch (current) {
        case '\\':
          builder.append("\\\\");
          break;
        case '"':
          builder.append("\\\"");
          break;
        case '\b':
          builder.append("\\b");
          break;
        case '\f':
          builder.append("\\f");
          break;
        case '\n':
          builder.append("\\n");
          break;
        case '\r':
          builder.append("\\r");
          break;
        case '\t':
          builder.append("\\t");
          break;
        default:
          if (current < 0x20) {
            builder.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
          } else {
            builder.append(current);
          }
      }
    }
    return builder.toString();
  }

  private static void writeAscii(OutputStream outputStream, String value) throws IOException {
    outputStream.write(value.getBytes(UTF_8));
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
      return outputStream.toString(UTF_8.name());
    }
  }
}
