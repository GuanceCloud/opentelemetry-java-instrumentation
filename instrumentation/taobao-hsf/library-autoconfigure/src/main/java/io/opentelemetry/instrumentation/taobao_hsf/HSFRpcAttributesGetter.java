package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;

enum HSFRpcAttributesGetter implements RpcAttributesGetter<HSFRequest> {
  INSTANCE;
  @Override
  public String getSystem(HSFRequest request) {
    return "taobao_hsf";
  }

  @Override
  public String getService(HSFRequest request) {
    return request.getService();
  }

  @Override
  public String getMethod(HSFRequest request) {
    return request.getMethod();
  }
}
