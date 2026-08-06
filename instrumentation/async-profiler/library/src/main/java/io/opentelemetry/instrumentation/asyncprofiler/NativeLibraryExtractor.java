/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import one.profiler.AsyncProfiler;

final class NativeLibraryExtractor {

  private NativeLibraryExtractor() {}

  static Path extract(LinuxPlatform platform, Path tempDir) throws IOException {
    Files.createDirectories(tempDir);

    Path targetDir = tempDir.resolve(asyncProfilerVersion()).resolve(platform.resourceDirectory());
    Path targetFile = targetDir.resolve("libasyncProfiler.so");
    if (Files.isRegularFile(targetFile)) {
      return targetFile;
    }

    Files.createDirectories(targetDir);
    Path tempFile = Files.createTempFile(targetDir, "async-profiler-", ".so.tmp");
    try (InputStream inputStream = openResource(platform.resourcePath())) {
      Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
      Files.move(
          tempFile,
          targetFile,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      return targetFile;
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private static InputStream openResource(String resourcePath) throws IOException {
    InputStream inputStream =
        AsyncProfiler.class.getClassLoader().getResourceAsStream(resourcePath);
    if (inputStream == null) {
      throw new IOException("Missing async-profiler native resource: " + resourcePath);
    }
    return inputStream;
  }

  private static String asyncProfilerVersion() {
    Package asyncProfilerPackage = AsyncProfiler.class.getPackage();
    String version =
        asyncProfilerPackage != null ? asyncProfilerPackage.getImplementationVersion() : null;
    return version != null && !version.isEmpty() ? version : "unknown";
  }
}
