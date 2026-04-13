/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

/** A single exported profiling snapshot. */
public interface ProfileSnapshot extends AutoCloseable {

  Instant getStartTime();

  Instant getEndTime();

  String getFormat();

  InputStream openStream() throws IOException;

  @Override
  void close() throws IOException;
}
