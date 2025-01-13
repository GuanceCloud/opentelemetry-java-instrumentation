/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachethrift;


import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;

//todo get value
enum ThriftRpcAttributesGetter implements RpcAttributesGetter<AbstractContext> {
  INSTANCE;

  @Override
  public String getSystem(AbstractContext request) {
    return "apache_thrift";
  }

  @Override
  public String getService(AbstractContext request) {
    return request.getSpanType();
  }

  @Override
  public String getMethod(AbstractContext request) {
    return request.getOperatorName();
  }
}
