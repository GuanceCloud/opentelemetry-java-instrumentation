/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.unmodifiableMap;

import java.util.LinkedHashMap;
import java.util.Map;

final class MetaEvent {

  private final long timestamp;
  private final String requestType;
  private final Map<String, Object> payload;

  MetaEvent(long timestamp, String requestType, Map<String, Object> payload) {
    this.timestamp = timestamp;
    this.requestType = requestType;
    this.payload = unmodifiableMap(new LinkedHashMap<>(payload));
  }

  long getTimestamp() {
    return timestamp;
  }

  String getRequestType() {
    return requestType;
  }

  Map<String, Object> getPayload() {
    return payload;
  }
}
