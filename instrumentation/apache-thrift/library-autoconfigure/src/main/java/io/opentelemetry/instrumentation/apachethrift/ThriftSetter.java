package io.opentelemetry.instrumentation.apachethrift;


import io.opentelemetry.context.propagation.TextMapSetter;

enum ThriftSetter implements TextMapSetter<AbstractContext> {

  INSTANCE;

  @Override
  public void set( AbstractContext carrier,  String key,  String value) {
    carrier.put(key, value);
  }
}
