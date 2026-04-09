/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class ProfilingScheduler implements AutoCloseable {

  private final ScheduledExecutorService executor;

  ProfilingScheduler(String threadName) {
    executor =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(true);
                thread.setContextClassLoader(null);
                return thread;
              }
            });
  }

  void schedule(Runnable runnable, long delay, TimeUnit unit) {
    executor.schedule(runnable, delay, unit);
  }

  void scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
    executor.scheduleAtFixedRate(runnable, initialDelay, period, unit);
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
