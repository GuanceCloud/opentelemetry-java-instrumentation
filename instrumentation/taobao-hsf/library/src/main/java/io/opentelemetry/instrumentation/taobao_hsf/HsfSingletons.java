package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class HsfSingletons {

  private static final Instrumenter<HsfRequest, Void> SERVER_INSTRUMENTER;
  private static final Instrumenter<HsfRequest, Void> CLIENT_INSTRUMENTER;


  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    HsfTelemetry thriftTelemetry = HsfTelemetry.create(openTelemetry);
    SERVER_INSTRUMENTER = thriftTelemetry.serverInstrumenter();
    CLIENT_INSTRUMENTER = thriftTelemetry.clientInstrumenter();
  }


  public static Instrumenter<HsfRequest, Void> serverInstrumenter(){
    return SERVER_INSTRUMENTER;
  }
  public static Instrumenter<HsfRequest, Void> clientInstrumenter(){
    return CLIENT_INSTRUMENTER;
  }
  private HsfSingletons() {
  }
}
