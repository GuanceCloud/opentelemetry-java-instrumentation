/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;

enum DubboRpcAttributesGetter implements RpcAttributesGetter<DubboRequest> {
  INSTANCE;

  @Override
  public String getSystem(DubboRequest request) {
    return "apache_dubbo";
  }

  @Override
  public String getService(DubboRequest request) {
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Override
  public String getMethod(DubboRequest request) {
    return request.invocation().getMethodName();
  }
}
