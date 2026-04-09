/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling.internal;

import io.opentelemetry.instrumentation.profiling.ProfileExporterAdapter;
import io.opentelemetry.instrumentation.profiling.ProfileSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal experimental file exporter used for profiling validation and debugging.
 *
 * <p>This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class FileProfileExporterAdapter implements ProfileExporterAdapter {

  private static final DateTimeFormatter fileTimestampFormatter =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmssSSS", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final Path directory;
  private final AtomicLong sequence = new AtomicLong();

  public FileProfileExporterAdapter(Path directory) {
    this.directory = directory;
  }

  @Override
  public void export(ProfileSnapshot snapshot) throws IOException {
    Files.createDirectories(directory);

    String extension = snapshot.getFormat().toLowerCase(Locale.ROOT);
    String filename =
        "profile-"
            + fileTimestampFormatter.format(snapshot.getStartTime())
            + "-"
            + fileTimestampFormatter.format(snapshot.getEndTime())
            + "-"
            + sequence.incrementAndGet()
            + "."
            + extension;

    Path target = directory.resolve(filename);
    Path temporary = Files.createTempFile(directory, "profile-", ".tmp");
    try (InputStream inputStream = snapshot.openStream()) {
      Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
