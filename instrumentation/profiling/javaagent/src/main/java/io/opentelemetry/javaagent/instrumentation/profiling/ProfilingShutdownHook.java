/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.profiling;

import io.opentelemetry.instrumentation.profiling.RuntimeProfiling;

final class ProfilingShutdownHook extends Thread {

  private final RuntimeProfiling runtimeProfiling;

  ProfilingShutdownHook(RuntimeProfiling runtimeProfiling) {
    super("OpenTelemetry Profiling Shutdown Hook");
    this.runtimeProfiling = runtimeProfiling;
    setContextClassLoader(null);
  }

  @Override
  public void run() {
    runtimeProfiling.close();
  }
}
