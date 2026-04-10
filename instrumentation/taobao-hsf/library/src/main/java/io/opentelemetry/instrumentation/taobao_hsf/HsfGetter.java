package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Optional;

enum HsfGetter implements TextMapGetter<HsfRequest> {
  INSTANCE;


  @Override
  public Iterable<String> keys(HsfRequest request) {
    return request.rpcContext().getAttachments().keySet();
  }

  @Override
  public String get(HsfRequest request, String key) {
    Object v = request.rpcContext().getAttachment(key);
    return Optional.ofNullable(v).map(Object::toString).orElse(null);
  }
}
