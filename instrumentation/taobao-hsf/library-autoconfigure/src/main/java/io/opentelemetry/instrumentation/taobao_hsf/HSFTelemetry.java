package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class HSFTelemetry {

  /** Returns a new {@link HSFTelemetry} configured with the given {@link OpenTelemetry}. */
  public static HSFTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link HSFTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static HSFTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new HSFTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HSFRequest, Void> serverInstrumenter;
  private final Instrumenter<HSFRequest, Void> clientInstrumenter;

  HSFTelemetry(
      Instrumenter<HSFRequest, Void> serverInstrumenter,
      Instrumenter<HSFRequest, Void> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  public Instrumenter<HSFRequest, Void> serverInstrumenter(){
    return serverInstrumenter;
  }
  public Instrumenter<HSFRequest, Void> clientInstrumenter(){
    return clientInstrumenter;
  }

}
