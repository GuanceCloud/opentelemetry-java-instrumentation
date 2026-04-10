package io.opentelemetry.instrumentation.taobao_hsf;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class HsfTelemetry {

  /** Returns a new {@link HsfTelemetry} configured with the given {@link OpenTelemetry}. */
  public static HsfTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link HsfTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static HsfTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new HsfTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HsfRequest, Void> serverInstrumenter;
  private final Instrumenter<HsfRequest, Void> clientInstrumenter;

  HsfTelemetry(
      Instrumenter<HsfRequest, Void> serverInstrumenter,
      Instrumenter<HsfRequest, Void> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  public Instrumenter<HsfRequest, Void> serverInstrumenter(){
    return serverInstrumenter;
  }
  public Instrumenter<HsfRequest, Void> clientInstrumenter(){
    return clientInstrumenter;
  }

}
