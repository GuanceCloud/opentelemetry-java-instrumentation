/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.instrumentation.profiling.ProfileSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProfileExporterAdapterTest {

  @TempDir Path tempDir;

  @Test
  void writesSnapshotToDisk() throws Exception {
    byte[] expected = new byte[] {1, 2, 3, 4};
    FileProfileExporterAdapter adapter = new FileProfileExporterAdapter(tempDir);

    adapter.export(new TestProfileSnapshot(expected));

    try (Stream<Path> files = Files.list(tempDir)) {
      List<Path> exported = files.collect(toList());
      assertEquals(1, exported.size());
      assertEquals(".jfr", fileExtension(exported.get(0)));
      assertArrayEquals(expected, Files.readAllBytes(exported.get(0)));
    }
  }

  private static String fileExtension(Path path) {
    String fileName = path.getFileName().toString();
    return fileName.substring(fileName.lastIndexOf('.'));
  }

  private static final class TestProfileSnapshot implements ProfileSnapshot {

    private final byte[] bytes;

    private TestProfileSnapshot(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public Instant getStartTime() {
      return Instant.parse("2026-01-01T00:00:00Z");
    }

    @Override
    public Instant getEndTime() {
      return Instant.parse("2026-01-01T00:00:01Z");
    }

    @Override
    public String getFormat() {
      return "jfr";
    }

    @Override
    public InputStream openStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void close() throws IOException {
      // Intentionally empty.
    }
  }
}
