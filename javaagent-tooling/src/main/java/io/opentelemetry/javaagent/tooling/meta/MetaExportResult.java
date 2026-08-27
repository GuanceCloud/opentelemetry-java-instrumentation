/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

enum MetaExportResult {
  SUCCESS,
  RETRYABLE_FAILURE,
  PERMANENT_FAILURE
}
