/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProfilingConfigTest {

  @Test
  void rejectsInvalidInterval() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProfilingConfig(
                Duration.ZERO,
                Duration.ZERO,
                Duration.ofMinutes(5),
                0,
                64,
                null,
                false,
                null,
                null));
  }

  @Test
  void rejectsNegativeStartupDelay() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProfilingConfig(
                Duration.ofSeconds(1),
                Duration.ofSeconds(-1),
                Duration.ofMinutes(5),
                0,
                64,
                null,
                false,
                null,
                null));
  }

  @Test
  void rejectsInvalidMaxAge() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProfilingConfig(
                Duration.ofSeconds(1),
                Duration.ZERO,
                Duration.ZERO,
                0,
                64,
                null,
                false,
                null,
                null));
  }

  @Test
  void keepsConfiguredValues() {
    ProfilingConfig config =
        new ProfilingConfig(
            Duration.ofSeconds(5),
            Duration.ofSeconds(2),
            Duration.ofMinutes(3),
            1024,
            128,
            null,
            true,
            Boolean.TRUE,
            Boolean.FALSE);

    assertEquals(Duration.ofSeconds(5), config.getInterval());
    assertEquals(Duration.ofSeconds(2), config.getStartupDelay());
    assertEquals(Duration.ofMinutes(3), config.getMaxAge());
    assertEquals(1024, config.getMaxSize());
    assertEquals(128, config.getStackDepth());
    assertEquals(true, config.isMemoryEnabled());
    assertEquals(true, config.getMemoryAllocationSampling());
    assertEquals(false, config.getMemoryOldObjectSampling());
  }
}
