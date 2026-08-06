/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LinuxPlatformTest {

  @Test
  void detectsLinuxX64() {
    assertEquals(
        LinuxPlatform.X64, LinuxPlatform.detect("Linux", "amd64").orElseThrow(AssertionError::new));
    assertEquals(
        LinuxPlatform.X64,
        LinuxPlatform.detect("linux", "x86_64").orElseThrow(AssertionError::new));
  }

  @Test
  void detectsLinuxArm64() {
    assertEquals(
        LinuxPlatform.ARM64,
        LinuxPlatform.detect("Linux", "aarch64").orElseThrow(AssertionError::new));
    assertEquals(
        LinuxPlatform.ARM64,
        LinuxPlatform.detect("Linux", "arm64").orElseThrow(AssertionError::new));
  }

  @Test
  void rejectsUnsupportedPlatforms() {
    assertFalse(LinuxPlatform.detect("Mac OS X", "x86_64").isPresent());
    assertFalse(LinuxPlatform.detect("Linux", "ppc64le").isPresent());
  }
}
