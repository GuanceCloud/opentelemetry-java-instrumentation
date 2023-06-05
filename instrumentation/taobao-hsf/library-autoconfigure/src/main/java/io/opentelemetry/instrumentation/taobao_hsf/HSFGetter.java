package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Optional;

enum HSFGetter implements TextMapGetter<HSFRequest> {
  INSTANCE;


  @Override
  public Iterable<String> keys(HSFRequest request) {
    return request.rpcContext().getAttachments().keySet();
  }

  @Override
  public String get(HSFRequest request, String key) {
    Object v = request.rpcContext().getAttachment(key);
    return Optional.ofNullable(v).map(Object::toString).orElse(null);
  }
}
