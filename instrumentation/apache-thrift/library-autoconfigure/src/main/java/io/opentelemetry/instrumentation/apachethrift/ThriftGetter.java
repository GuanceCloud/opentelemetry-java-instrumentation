package io.opentelemetry.instrumentation.apachethrift;


import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

enum ThriftGetter implements TextMapGetter<AbstractContext> {
  INSTANCE;

  @Override
  public Iterable<String> keys(AbstractContext carrier) {
    return carrier.keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable AbstractContext carrier, String key) {
    return carrier.get(key);
  }
}
