/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import java.util.Locale;
import java.util.Optional;

enum LinuxPlatform {
  X64("linux-x64"),
  ARM64("linux-arm64");

  private final String resourceDirectory;

  LinuxPlatform(String resourceDirectory) {
    this.resourceDirectory = resourceDirectory;
  }

  String resourcePath() {
    return resourceDirectory + "/libasyncProfiler.so";
  }

  String resourceDirectory() {
    return resourceDirectory;
  }

  static Optional<LinuxPlatform> current() {
    return detect(System.getProperty("os.name"), System.getProperty("os.arch"));
  }

  static Optional<LinuxPlatform> detect(String osName, String osArch) {
    if (osName == null || osArch == null) {
      return Optional.empty();
    }
    String normalizedOs = osName.toLowerCase(Locale.ROOT);
    if (!normalizedOs.contains("linux")) {
      return Optional.empty();
    }

    String normalizedArch = osArch.toLowerCase(Locale.ROOT);
    if ("amd64".equals(normalizedArch) || "x86_64".equals(normalizedArch)) {
      return Optional.of(X64);
    }
    if ("aarch64".equals(normalizedArch) || "arm64".equals(normalizedArch)) {
      return Optional.of(ARM64);
    }
    return Optional.empty();
  }
}
