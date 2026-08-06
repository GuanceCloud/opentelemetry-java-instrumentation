/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import io.opentelemetry.instrumentation.asyncprofiler.AsyncProfilerRuntime;

final class AsyncProfilerShutdownHook extends Thread {

  private final AsyncProfilerRuntime runtime;

  AsyncProfilerShutdownHook(AsyncProfilerRuntime runtime) {
    super(runtime::close, "OpenTelemetry AsyncProfilerShutdownHook");
    this.runtime = runtime;
  }

  @Override
  public void run() {
    runtime.close();
  }
}
