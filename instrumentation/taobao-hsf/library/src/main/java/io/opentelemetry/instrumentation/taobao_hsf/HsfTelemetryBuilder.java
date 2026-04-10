package io.opentelemetry.instrumentation.taobao_hsf;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.api.common.AttributeKey;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class HsfTelemetryBuilder {


  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-thrift";
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<HsfRequest, Void>> attributesExtractors =
      new ArrayList<>();

  HsfTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }


  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public HsfTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<HsfRequest, Void> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link HsfTelemetry} with the settings of this {@link HsfTelemetryBuilder}.
   */
  public HsfTelemetry build() {
    HsfRpcAttributesGetter rpcAttributesGetter = HsfRpcAttributesGetter.INSTANCE;
    SpanNameExtractor<HsfRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);

    InstrumenterBuilder<HsfRequest, Void> serverInstrumenterBuilder =
        Instrumenter.<HsfRequest, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    InstrumenterBuilder<HsfRequest, Void> clientInstrumenterBuilder =
        Instrumenter.<HsfRequest, Void>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(PEER_SERVICE, peerService));
    }

    return new HsfTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(HsfGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(HsfSetter.INSTANCE));
  }
}
