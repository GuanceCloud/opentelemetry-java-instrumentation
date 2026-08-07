/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import javax.annotation.Nullable;

public final class SpringAiAlibabaAgentSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-ai-alibaba-agent-1.0";

  private static final Instrumenter<AgentRequest, Void> AGENT_INSTRUMENTER = createAgentInstrumenter();
  private static final Instrumenter<ToolRequest, ToolCallResponse> TOOL_INSTRUMENTER =
      createToolInstrumenter();

  public static Instrumenter<AgentRequest, Void> agentInstrumenter() {
    return AGENT_INSTRUMENTER;
  }

  public static Instrumenter<ToolRequest, ToolCallResponse> toolInstrumenter() {
    return TOOL_INSTRUMENTER;
  }

  private static Instrumenter<AgentRequest, Void> createAgentInstrumenter() {
    return Instrumenter.<AgentRequest, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, SpringAiAlibabaAgentSpanNames::agent)
        .addAttributesExtractor(new AgentAttributesExtractor())
        .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  private static Instrumenter<ToolRequest, ToolCallResponse> createToolInstrumenter() {
    InstrumenterBuilder<ToolRequest, ToolCallResponse> builder =
        Instrumenter.<ToolRequest, ToolCallResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, SpringAiAlibabaAgentSpanNames::tool)
            .addAttributesExtractor(new ToolAttributesExtractor())
            .setSpanStatusExtractor(
                (spanStatusBuilder, request, response, error) -> {
                  if (response != null && response.isError()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
                  }
                });
    return builder.buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  private static final class AgentAttributesExtractor implements AttributesExtractor<AgentRequest, Void> {
    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, AgentRequest request) {
      attributes.put(stringKey("gen_ai.agent.name"), request.getAgentName());
      attributes.put(stringKey("gen_ai.operation.name"), "invoke_agent");
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        AgentRequest request,
        @Nullable Void response,
        @Nullable Throwable error) {}
  }

  private static final class ToolAttributesExtractor
      implements AttributesExtractor<ToolRequest, ToolCallResponse> {
    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, ToolRequest request) {
      attributes.put(stringKey("gen_ai.operation.name"), "execute_tool");
      attributes.put(stringKey("gen_ai.agent.name"), request.getAgentName());
      attributes.put(stringKey("gen_ai.tool.name"), request.getToolName());
      attributes.put(stringKey("gen_ai.tool.call.id"), request.getToolCallId());
      attributes.put(stringKey("gen_ai.tool.call.arguments"), request.getArguments());
      attributes.put(
          booleanKey("spring_ai_alibaba.tool.parallel_execution"), request.isParallelExecution());
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        ToolRequest request,
        @Nullable ToolCallResponse response,
        @Nullable Throwable error) {
      if (response != null && !response.isError() && response.getResult() != null) {
        attributes.put(stringKey("gen_ai.tool.call.result"), response.getResult());
      }
    }
  }

  private SpringAiAlibabaAgentSingletons() {}
}
