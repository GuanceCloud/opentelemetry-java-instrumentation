/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import io.opentelemetry.instrumentation.profiling.RuntimeProfiling;

enum NoopRuntimeProfiling implements RuntimeProfiling {
  INSTANCE;

  @Override
  public void start() {
    // Intentionally empty.
  }

  @Override
  public void close() {
    // Intentionally empty.
  }
}
