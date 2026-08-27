/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.api.impl.InstrumentationUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;

final class HttpMetaExporter implements MetaExporter {

  private static final Logger logger = Logger.getLogger(HttpMetaExporter.class.getName());
  private static final long MAX_RETRY_DELAY_MILLIS = SECONDS.toMillis(10);
  private static final int MAX_RESPONSE_BODY_BYTES = 64 * 1024;

  private final URL endpoint;
  private final Map<String, String> headers;
  private final int timeoutMillis;
  private final boolean gzipEnabled;
  private final int maxRetries;
  private final int maxRequestSizeBytes;
  private final MetaJsonSerializer serializer;
  private final Sleeper sleeper;

  HttpMetaExporter(MetaConfiguration configuration) {
    this(
        configuration.getEndpoint(),
        configuration.getHeaders(),
        configuration.getTimeoutMillis(),
        configuration.isGzipEnabled(),
        configuration.getMaxRetries(),
        configuration.getMaxRequestSizeBytes(),
        new MetaJsonSerializer(),
        Thread::sleep);
  }

  HttpMetaExporter(
      URL endpoint,
      Map<String, String> headers,
      int timeoutMillis,
      boolean gzipEnabled,
      int maxRetries,
      MetaJsonSerializer serializer,
      Sleeper sleeper) {
    this(
        endpoint,
        headers,
        timeoutMillis,
        gzipEnabled,
        maxRetries,
        Integer.MAX_VALUE,
        serializer,
        sleeper);
  }

  HttpMetaExporter(
      URL endpoint,
      Map<String, String> headers,
      int timeoutMillis,
      boolean gzipEnabled,
      int maxRetries,
      int maxRequestSizeBytes,
      MetaJsonSerializer serializer,
      Sleeper sleeper) {
    this.endpoint = endpoint;
    this.headers = headers;
    this.timeoutMillis = timeoutMillis;
    this.gzipEnabled = gzipEnabled;
    this.maxRetries = maxRetries;
    this.maxRequestSizeBytes = maxRequestSizeBytes;
    this.serializer = serializer;
    this.sleeper = sleeper;
  }

  @Override
  public MetaExportResult export(MetaExportRequest request) {
    MetaExportResult[] result = {MetaExportResult.PERMANENT_FAILURE};
    try {
      InstrumentationUtil.suppressInstrumentation(() -> result[0] = exportSuppressed(request));
    } catch (RuntimeException e) {
      logFailure(request, 0, 0, "unexpected_error");
      logger.log(FINE, "Meta export failed unexpectedly", e);
    }
    return result[0];
  }

  private MetaExportResult exportSuppressed(MetaExportRequest request) {
    RequestBody body;
    try {
      body = serializeRequest(request);
    } catch (IOException e) {
      RequestTooLargeException tooLarge = findRequestTooLarge(e);
      if (tooLarge != null) {
        logFailure(request, 0, 0, tooLarge.failureReason);
      } else if (e instanceof JsonProcessingException) {
        logFailure(request, 0, 0, "serialization_error");
        logger.log(FINE, "Unable to serialize meta export request", e);
      } else {
        String failureReason = gzipEnabled ? "compression_error" : "serialization_error";
        logFailure(request, 0, 0, failureReason);
        logger.log(FINE, "Unable to create meta export request body", e);
      }
      return MetaExportResult.PERMANENT_FAILURE;
    }

    for (int attempt = 0; ; attempt++) {
      AttemptResult attemptResult = send(body);
      if (attemptResult.success) {
        logSuccess(request, attemptResult.statusCode, attempt + 1);
        return MetaExportResult.SUCCESS;
      }
      if (!attemptResult.retryable || attempt >= maxRetries) {
        logFailure(request, attemptResult.statusCode, attempt + 1, attemptResult.failureReason);
        return attemptResult.retryable
            ? MetaExportResult.RETRYABLE_FAILURE
            : MetaExportResult.PERMANENT_FAILURE;
      }

      long delayMillis =
          attemptResult.retryAfterMillis > 0
              ? attemptResult.retryAfterMillis
              : retryDelayMillis(attempt);
      try {
        sleeper.sleep(Math.min(delayMillis, MAX_RETRY_DELAY_MILLIS));
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        logFailure(request, attemptResult.statusCode, attempt + 1, "interrupted");
        return MetaExportResult.RETRYABLE_FAILURE;
      }
    }
  }

  private RequestBody serializeRequest(MetaExportRequest request) throws IOException {
    BoundedBuffer body =
        new BoundedBuffer(
            maxRequestSizeBytes,
            gzipEnabled ? "compressed_request_too_large" : "request_too_large");
    if (gzipEnabled) {
      try (GZIPOutputStream gzipOutput = new GZIPOutputStream(body)) {
        serializer.serialize(
            request,
            new SizeLimitingOutputStream(gzipOutput, maxRequestSizeBytes, "request_too_large"));
      }
    } else {
      serializer.serialize(request, body);
    }
    return body.toRequestBody();
  }

  private AttemptResult send(RequestBody body) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) endpoint.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setUseCaches(false);
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(timeoutMillis);
      connection.setReadTimeout(timeoutMillis);
      connection.setFixedLengthStreamingMode(body.length);
      for (Map.Entry<String, String> header : headers.entrySet()) {
        connection.setRequestProperty(header.getKey(), header.getValue());
      }
      connection.setRequestProperty("Content-Type", "application/json");
      if (gzipEnabled) {
        connection.setRequestProperty("Content-Encoding", "gzip");
      }

      try (OutputStream outputStream = connection.getOutputStream()) {
        outputStream.write(body.bytes, 0, body.length);
      }

      int responseCode = connection.getResponseCode();
      String retryAfter = connection.getHeaderField("Retry-After");
      try {
        drainResponse(connection, responseCode);
      } catch (IOException e) {
        logger.log(FINE, "Unable to drain meta export response for HTTP status {0}", responseCode);
      }
      if (responseCode >= 200 && responseCode < 300) {
        return AttemptResult.success(responseCode);
      }
      boolean retryable =
          responseCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT
              || responseCode == 429
              || responseCode >= 500;
      return AttemptResult.failure(
          responseCode, retryable, parseRetryAfterMillis(retryAfter), "http_response");
    } catch (IOException e) {
      logger.log(FINE, "Meta export request failed with an I/O error: {0}", e.getClass().getName());
      return AttemptResult.failure(-1, true, 0, e.getClass().getName());
    } catch (RuntimeException e) {
      logger.log(FINE, "Meta export request failed unexpectedly", e);
      return AttemptResult.failure(-1, false, 0, e.getClass().getName());
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  @Nullable
  private static RequestTooLargeException findRequestTooLarge(IOException exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof RequestTooLargeException) {
        return (RequestTooLargeException) current;
      }
      current = current.getCause();
    }
    return null;
  }

  private static void drainResponse(HttpURLConnection connection, int responseCode)
      throws IOException {
    InputStream stream =
        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
    if (stream == null) {
      return;
    }
    try {
      byte[] buffer = new byte[8192];
      int totalBytes = 0;
      int read;
      while ((read = stream.read(buffer)) >= 0) {
        // Drain the response so HttpURLConnection can reuse the connection.
        totalBytes += read;
        if (totalBytes >= MAX_RESPONSE_BODY_BYTES) {
          break;
        }
      }
    } finally {
      stream.close();
    }
  }

  private static long parseRetryAfterMillis(String value) {
    if (value == null || value.trim().isEmpty()) {
      return 0;
    }
    String trimmed = value.trim();
    try {
      return Math.max(0, SECONDS.toMillis(Long.parseLong(trimmed)));
    } catch (NumberFormatException ignored) {
      try {
        long retryAtMillis =
            ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli();
        return Math.max(0, retryAtMillis - System.currentTimeMillis());
      } catch (DateTimeParseException ignore) {
        return 0;
      }
    }
  }

  private static long retryDelayMillis(int attempt) {
    long baseDelay = Math.min(200L << Math.min(attempt, 5), MAX_RETRY_DELAY_MILLIS);
    return ThreadLocalRandom.current().nextLong(baseDelay / 2, baseDelay + 1);
  }

  private void logSuccess(MetaExportRequest request, int statusCode, int attempts) {
    logger.log(
        INFO,
        "Meta export succeeded: endpoint={0}, status_code={1}, seq_id={2}, event_count={3}, attempts={4}",
        new Object[] {
          endpointForLogging(),
          statusCode,
          request.getSequenceId(),
          request.getEvents().size(),
          attempts
        });
  }

  private void logFailure(
      MetaExportRequest request, int statusCode, int attempts, String failureReason) {
    logger.log(
        WARNING,
        "Meta export failed: endpoint={0}, status_code={1}, seq_id={2}, event_count={3}, attempts={4}, reason={5}",
        new Object[] {
          endpointForLogging(),
          statusCode > 0 ? statusCode : "unavailable",
          request.getSequenceId(),
          request.getEvents().size(),
          attempts,
          failureReason
        });
  }

  private String endpointForLogging() {
    StringBuilder value = new StringBuilder();
    value.append(endpoint.getProtocol()).append("://").append(endpoint.getHost());
    if (endpoint.getPort() >= 0) {
      value.append(':').append(endpoint.getPort());
    }
    value.append(endpoint.getPath());
    return value.toString();
  }

  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  private static final class AttemptResult {
    private final boolean success;
    private final int statusCode;
    private final boolean retryable;
    private final long retryAfterMillis;
    private final String failureReason;

    private static AttemptResult success(int statusCode) {
      return new AttemptResult(true, statusCode, false, 0, "none");
    }

    private static AttemptResult failure(
        int statusCode, boolean retryable, long retryAfterMillis, String failureReason) {
      return new AttemptResult(false, statusCode, retryable, retryAfterMillis, failureReason);
    }

    private AttemptResult(
        boolean success,
        int statusCode,
        boolean retryable,
        long retryAfterMillis,
        String failureReason) {
      this.success = success;
      this.statusCode = statusCode;
      this.retryable = retryable;
      this.retryAfterMillis = retryAfterMillis;
      this.failureReason = failureReason;
    }
  }

  private static final class RequestBody {
    private final byte[] bytes;
    private final int length;

    private RequestBody(byte[] bytes, int length) {
      this.bytes = bytes;
      this.length = length;
    }
  }

  private static final class BoundedBuffer extends OutputStream {
    private static final int INITIAL_CAPACITY = 8 * 1024;

    private final int maxBytes;
    private final String failureReason;
    private byte[] buffer;
    private int count;

    private BoundedBuffer(int maxBytes, String failureReason) {
      this.maxBytes = maxBytes;
      this.failureReason = failureReason;
      this.buffer = new byte[Math.min(INITIAL_CAPACITY, maxBytes)];
    }

    @Override
    public void write(int value) throws IOException {
      ensureCapacity(1);
      buffer[count++] = (byte) value;
    }

    @Override
    public void write(byte[] value, int offset, int length) throws IOException {
      if (offset < 0 || length < 0 || offset > value.length - length) {
        throw new IndexOutOfBoundsException();
      }
      ensureCapacity(length);
      System.arraycopy(value, offset, buffer, count, length);
      count += length;
    }

    private void ensureCapacity(int additionalBytes) throws RequestTooLargeException {
      if (additionalBytes > maxBytes - count) {
        throw new RequestTooLargeException(failureReason);
      }
      int requiredCapacity = count + additionalBytes;
      if (requiredCapacity <= buffer.length) {
        return;
      }
      int doubledCapacity = buffer.length > maxBytes / 2 ? maxBytes : buffer.length * 2;
      buffer = Arrays.copyOf(buffer, Math.max(requiredCapacity, doubledCapacity));
    }

    private RequestBody toRequestBody() {
      return new RequestBody(buffer, count);
    }
  }

  private static final class SizeLimitingOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final int maxBytes;
    private final String failureReason;
    private int count;

    private SizeLimitingOutputStream(OutputStream delegate, int maxBytes, String failureReason) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
      this.failureReason = failureReason;
    }

    @Override
    public void write(int value) throws IOException {
      ensureCapacity(1);
      delegate.write(value);
      count++;
    }

    @Override
    public void write(byte[] value, int offset, int length) throws IOException {
      if (length > maxBytes - count) {
        throw new RequestTooLargeException(failureReason);
      }
      delegate.write(value, offset, length);
      count += length;
    }

    private void ensureCapacity(int additionalBytes) throws RequestTooLargeException {
      if (additionalBytes > maxBytes - count) {
        throw new RequestTooLargeException(failureReason);
      }
    }
  }

  private static final class RequestTooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    private final String failureReason;

    private RequestTooLargeException(String failureReason) {
      super(failureReason);
      this.failureReason = failureReason;
    }
  }
}
