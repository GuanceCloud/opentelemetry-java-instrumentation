package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.context.propagation.TextMapSetter;

public enum HSFSetter implements TextMapSetter<HSFRequest> {
  INSTANCE;

  @Override
  public void set(HSFRequest request, String key, String value) {
//    request.rpcContext().putAttachment(key, value);
    request.rpcContext().getAttachments().put(key, value);
  }
}
