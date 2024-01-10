/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachethrift;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** A builder of {@link ThriftTelemetry}. */
public final class ThriftTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-thrift";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<AbstractContext, Void>> attributesExtractors =
      new ArrayList<>();

  ThriftTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  public void setPeerService(String peerService) {
    this.peerService = peerService;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public ThriftTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<AbstractContext, Void> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link ThriftTelemetry} with the settings of this {@link ThriftTelemetryBuilder}.
   */
  public ThriftTelemetry build() {
    ThriftRpcAttributesGetter rpcAttributesGetter = ThriftRpcAttributesGetter.INSTANCE;
    SpanNameExtractor<AbstractContext> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);

    InstrumenterBuilder<AbstractContext, Void> serverInstrumenterBuilder =
        Instrumenter.<AbstractContext, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    InstrumenterBuilder<AbstractContext, Void> clientInstrumenterBuilder =
        Instrumenter.<AbstractContext, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    }

    return new ThriftTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(ThriftGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(ThriftSetter.INSTANCE));
  }
}
