package io.opentelemetry.instrumentation.apachethrift;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class ThriftSingletons {

  private static final Instrumenter<AbstractContext, Void> SERVER_INSTRUMENTER;
  private static final Instrumenter<AbstractContext, Void> CLIENT_INSTRUMENTER;


  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    ThriftTelemetry thriftTelemetry = ThriftTelemetry.create(openTelemetry);
    SERVER_INSTRUMENTER = thriftTelemetry.serverInstrumenter();
    CLIENT_INSTRUMENTER = thriftTelemetry.clientInstrumenter();
  }


  public static Instrumenter<AbstractContext, Void> serverInstrumenter(){
    return SERVER_INSTRUMENTER;
  }
  public static Instrumenter<AbstractContext, Void> clientInstrumenter(){
    return CLIENT_INSTRUMENTER;
  }
  private ThriftSingletons(){

  }
}
