/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import java.time.Duration;

public class AsyncProfilerSmokeMain {

  private AsyncProfilerSmokeMain() {}

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("alloc")) {
      doAllocationWork(Duration.ofSeconds(2));
      return;
    }
    if (args.length > 0 && args[0].equals("exception")) {
      doExceptionWork(Duration.ofSeconds(2));
      return;
    }

    doCpuWork(Duration.ofSeconds(2));
  }

  private static void doCpuWork(Duration duration) {
    long end = System.nanoTime() + duration.toNanos();
    long state = 1L;
    while (System.nanoTime() < end) {
      state = state * 1664525L + 1013904223L;
    }
    if (state == Long.MIN_VALUE) {
      throw new AssertionError("Unreachable");
    }
  }

  private static void doAllocationWork(Duration duration) {
    long end = System.nanoTime() + duration.toNanos();
    byte[][] ring = new byte[256][];
    int index = 0;
    long state = 1L;
    while (System.nanoTime() < end) {
      ring[index++ & 255] = new byte[8 * 1024];
      state = state * 1103515245L + 12345L;
    }
    if (state == Long.MIN_VALUE) {
      throw new AssertionError("Unreachable");
    }
  }

  private static void doExceptionWork(Duration duration) {
    long end = System.nanoTime() + duration.toNanos();
    long state = 1L;
    while (System.nanoTime() < end) {
      try {
        throw new IllegalStateException("boom-" + state);
      } catch (IllegalStateException ignored) {
        state = state * 1103515245L + 12345L;
      }
    }
    if (state == Long.MIN_VALUE) {
      throw new AssertionError("Unreachable");
    }
  }
}
