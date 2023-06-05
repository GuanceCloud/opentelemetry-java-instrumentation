package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class HSFSingletons {

  private static final Instrumenter<HSFRequest, Void> SERVER_INSTRUMENTER;
  private static final Instrumenter<HSFRequest, Void> CLIENT_INSTRUMENTER;


  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    HSFTelemetry thriftTelemetry = HSFTelemetry.create(openTelemetry);
    SERVER_INSTRUMENTER = thriftTelemetry.serverInstrumenter();
    CLIENT_INSTRUMENTER = thriftTelemetry.clientInstrumenter();
  }


  public static Instrumenter<HSFRequest, Void> serverInstrumenter(){
    return SERVER_INSTRUMENTER;
  }
  public static Instrumenter<HSFRequest, Void> clientInstrumenter(){
    return CLIENT_INSTRUMENTER;
  }
  private HSFSingletons() {
  }
}
