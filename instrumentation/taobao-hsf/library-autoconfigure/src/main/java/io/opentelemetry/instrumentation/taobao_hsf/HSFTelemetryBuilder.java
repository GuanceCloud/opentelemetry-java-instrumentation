package io.opentelemetry.instrumentation.taobao_hsf;

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

public final class HSFTelemetryBuilder {


  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-thrift";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<HSFRequest, Void>> attributesExtractors =
      new ArrayList<>();

  HSFTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }


  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public HSFTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HSFRequest, Void> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link HSFTelemetry} with the settings of this {@link HSFTelemetryBuilder}.
   */
  public HSFTelemetry build() {
    HSFRpcAttributesGetter rpcAttributesGetter = HSFRpcAttributesGetter.INSTANCE;
    SpanNameExtractor<HSFRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);

    InstrumenterBuilder<HSFRequest, Void> serverInstrumenterBuilder =
        Instrumenter.<HSFRequest, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    InstrumenterBuilder<HSFRequest, Void> clientInstrumenterBuilder =
        Instrumenter.<HSFRequest, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    }

    return new HSFTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(HSFGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(HSFSetter.INSTANCE));
  }
}
