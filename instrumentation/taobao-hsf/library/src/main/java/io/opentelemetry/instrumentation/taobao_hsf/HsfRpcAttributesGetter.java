package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;

enum HsfRpcAttributesGetter implements RpcAttributesGetter<HsfRequest> {
  INSTANCE;
  @Override
  public String getSystem(HsfRequest request) {
    return "taobao_hsf";
  }

  @Override
  public String getService(HsfRequest request) {
    return request.getService();
  }

  @Override
  public String getMethod(HsfRequest request) {
    return request.getMethod();
  }
}
