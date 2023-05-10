/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachethrift;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting Apache Dubbo servers and clients. */
public final class ThriftTelemetry {

  /** Returns a new {@link ThriftTelemetry} configured with the given {@link OpenTelemetry}. */
  public static ThriftTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link ThriftTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static ThriftTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new ThriftTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<AbstractContext, Void> serverInstrumenter;
  private final Instrumenter<AbstractContext, Void> clientInstrumenter;

  ThriftTelemetry(
      Instrumenter<AbstractContext, Void> serverInstrumenter,
      Instrumenter<AbstractContext, Void> clientInstrumenter) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
  }

  public Instrumenter<AbstractContext, Void> serverInstrumenter(){
    return serverInstrumenter;
  }
  public Instrumenter<AbstractContext, Void> clientInstrumenter(){
    return clientInstrumenter;
  }

}
