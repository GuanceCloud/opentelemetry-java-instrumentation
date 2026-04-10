package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.context.propagation.TextMapSetter;

public enum HsfSetter implements TextMapSetter<HsfRequest> {
  INSTANCE;

  @Override
  public void set(HsfRequest request, String key, String value) {
//    request.rpcContext().putAttachment(key, value);
    request.rpcContext().getAttachments().put(key, value);
  }
}
