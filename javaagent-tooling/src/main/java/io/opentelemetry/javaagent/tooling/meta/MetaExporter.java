/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

interface MetaExporter {

  MetaExportResult export(MetaExportRequest request);

  default void shutdown() {}
}
