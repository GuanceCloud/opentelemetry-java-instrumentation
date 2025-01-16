/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.internal;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.thrift.ThriftRequest;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThriftNetServerAttributesGetter
    implements ServerAttributesGetter<ThriftRequest> {
  @Override
  public String getServerAddress(ThriftRequest request) {
    return request.host;
  }

  @Override
  public Integer getServerPort(ThriftRequest request) {
    return request.port;
  }
}
