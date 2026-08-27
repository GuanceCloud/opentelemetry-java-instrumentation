/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MetaExportRequest {

  private static final String API_VERSION = "v1";

  private final long sequenceId;
  private final long tracerTime;
  private final String runtimeId;
  private final Map<String, Object> resource;
  private final List<MetaEvent> events;

  MetaExportRequest(
      long sequenceId,
      long tracerTime,
      String runtimeId,
      Map<String, Object> resource,
      List<MetaEvent> events) {
    this.sequenceId = sequenceId;
    this.tracerTime = tracerTime;
    this.runtimeId = runtimeId;
    this.resource = unmodifiableMap(new LinkedHashMap<>(resource));
    this.events = unmodifiableList(new ArrayList<>(events));
  }

  String getApiVersion() {
    return API_VERSION;
  }

  String getRuntimeId() {
    return runtimeId;
  }

  long getSequenceId() {
    return sequenceId;
  }

  long getTracerTime() {
    return tracerTime;
  }

  Map<String, Object> getResource() {
    return resource;
  }

  List<MetaEvent> getEvents() {
    return events;
  }
}
