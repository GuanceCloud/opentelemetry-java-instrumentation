/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.profiling;

/** Consumes profiling snapshots produced by the runtime profiling subsystem. */
public interface ProfileExporterAdapter {

  void export(ProfileSnapshot snapshot) throws Exception;
}
