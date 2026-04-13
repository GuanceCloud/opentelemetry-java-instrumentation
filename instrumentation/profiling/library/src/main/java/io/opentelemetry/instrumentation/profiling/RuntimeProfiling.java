/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling;

/** Represents a running profiling subsystem. */
public interface RuntimeProfiling extends AutoCloseable {

  static RuntimeProfilingBuilder builder() {
    return new RuntimeProfilingBuilder();
  }

  /** Starts profiling. This method is idempotent. */
  void start();

  @Override
  void close();
}
