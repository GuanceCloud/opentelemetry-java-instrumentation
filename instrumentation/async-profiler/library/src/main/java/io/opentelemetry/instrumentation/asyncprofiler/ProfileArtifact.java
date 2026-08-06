/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.time.Instant;

/** Immutable description of a generated profile artifact. */
public final class ProfileArtifact {

  private final Path path;
  private final Instant startTime;
  private final Instant endTime;
  private final String format;

  public ProfileArtifact(Path path, Instant startTime, Instant endTime, String format) {
    this.path = requireNonNull(path, "path");
    this.startTime = requireNonNull(startTime, "startTime");
    this.endTime = requireNonNull(endTime, "endTime");
    this.format = requireNonNull(format, "format");
  }

  public Path getPath() {
    return path;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public String getFormat() {
    return format;
  }
}
