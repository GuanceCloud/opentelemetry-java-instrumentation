/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.asyncprofiler;

import java.io.IOException;

/** Exports a generated profile artifact. */
public interface ProfileExporter {

  void export(ProfileArtifact artifact) throws IOException;

  static ProfileExporter noop() {
    return artifact -> {};
  }
}
