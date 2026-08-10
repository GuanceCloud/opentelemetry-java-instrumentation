/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.node.AgentToolNode;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

class AgentFrameworkInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void streamCreatesAgentSpanAndMakesItParentOfChatModelSpan() throws GraphRunnerException {
    TestAgent agent = new TestAgent();

    testing.runWithSpan("parent", () -> agent.stream("Tell me about traces").blockLast());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span
                        .hasName("stream_agent test-agent")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("gen_ai.agent.name"), "test-agent"),
                            equalTo(stringKey("gen_ai.operation.name"), "invoke_agent")),
                span ->
                    span
                        .hasName("chat test-model")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))));
  }

  @Test
  void invokeCreatesAgentSpanAndMakesItParentOfChatModelSpan() throws GraphRunnerException {
    TestAgent agent = new TestAgent();

    testing.runWithSpan("parent", () -> agent.invoke("Tell me about traces"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span
                        .hasName("invoke_agent test-agent")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("gen_ai.agent.name"), "test-agent"),
                            equalTo(stringKey("gen_ai.operation.name"), "invoke_agent")),
                span ->
                    span
                        .hasName("chat test-model")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))));
  }

  @Test
  void toolCallCreatesSpan() throws Exception {
    AgentToolNode toolNode = toolNode(new TestTool("weather", false));

    testing.runWithSpan("parent", () -> toolNode.apply(state("weather"), RunnableConfig.builder().build()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span
                        .hasName("execute_tool weather")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("gen_ai.operation.name"), "execute_tool"),
                            equalTo(stringKey("gen_ai.agent.name"), "test-agent"),
                            equalTo(stringKey("gen_ai.tool.name"), "weather"),
                            equalTo(stringKey("gen_ai.tool.call.id"), "tool-call-id"),
                            equalTo(stringKey("gen_ai.tool.call.arguments"), "{}"),
                            equalTo(
                                booleanKey("spring_ai_alibaba.tool.parallel_execution"), false),
                            equalTo(stringKey("gen_ai.tool.call.result"), "weather is sunny"))));
  }

  @Test
  void failedToolCallMarksSpanAsError() throws Exception {
    AgentToolNode toolNode = toolNode(new TestTool("weather", true));

    testing.runWithSpan("parent", () -> toolNode.apply(state("weather"), RunnableConfig.builder().build()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span
                        .hasName("execute_tool weather")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("gen_ai.operation.name"), "execute_tool"),
                            equalTo(stringKey("gen_ai.agent.name"), "test-agent"),
                            equalTo(stringKey("gen_ai.tool.name"), "weather"),
                            equalTo(stringKey("gen_ai.tool.call.id"), "tool-call-id"),
                            equalTo(stringKey("gen_ai.tool.call.arguments"), "{}"),
                            equalTo(
                                booleanKey("spring_ai_alibaba.tool.parallel_execution"), false))));
  }

  private static AgentToolNode toolNode(ToolCallback toolCallback) {
    return AgentToolNode.builder()
        .agentName("test-agent")
        .toolCallbacks(singletonList(toolCallback))
        .toolExecutionExceptionProcessor(null)
        .build();
  }

  private static OverAllState state(String toolName) throws ReflectiveOperationException {
    Class<?> toolCallType =
        Class.forName("org.springframework.ai.chat.messages.AssistantMessage$ToolCall");
    Object toolCall =
        toolCallType
            .getConstructor(String.class, String.class, String.class, String.class)
            .newInstance("tool-call-id", "function", toolName, "{}");
    Object builder = AssistantMessage.builder();
    builder.getClass().getMethod("content", String.class).invoke(builder, "");
    builder.getClass().getMethod("toolCalls", List.class).invoke(builder, singletonList(toolCall));
    Message message = (Message) builder.getClass().getMethod("build").invoke(builder);
    return new OverAllState(singletonMap("messages", new ArrayList<>(singletonList(message))));
  }

  private static final class TestAgent extends Agent {
    private final ChatModel chatModel = new TestChatModel();

    TestAgent() {
      super("test-agent", "Test agent");
    }

    @Override
    protected StateGraph initGraph() throws GraphStateException {
      return new StateGraph()
          .addNode(
              "model",
              state -> {
                DefaultChatOptions options = new DefaultChatOptions();
                options.setModel("test-model");
                chatModel.call(new Prompt("Tell me about traces", options));
                return completedFuture(emptyMap());
              })
          .addEdge(StateGraph.START, "model")
          .addEdge("model", StateGraph.END);
    }
  }

  private static final class TestChatModel implements ChatModel {
    @Override
    public ChatResponse call(Prompt prompt) {
      return response();
    }

    @Override
    @SuppressWarnings("PublicApiNamedStreamShouldReturnStream")
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.just(response());
    }

    private static ChatResponse response() {
      Generation generation =
          new Generation(
              new AssistantMessage("A trace represents an end-to-end request."),
              ChatGenerationMetadata.builder().finishReason("stop").build());
      ChatResponseMetadata metadata =
          ChatResponseMetadata.builder()
              .id("response-id")
              .model("test-model")
              .usage(new DefaultUsage(3, 2))
              .build();
      return new ChatResponse(singletonList(generation), metadata);
    }
  }

  private static final class TestTool implements ToolCallback {
    private final String name;
    private final boolean fail;

    TestTool(String name, boolean fail) {
      this.name = name;
      this.fail = fail;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder()
          .name(name)
          .description("Test tool")
          .inputSchema("{\"type\":\"object\",\"properties\":{}}")
          .build();
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
      if (fail) {
        throw new IllegalStateException("tool failure");
      }
      return "weather is sunny";
    }

    @Override
    public String call(String toolInput) {
      return call(toolInput, new ToolContext(emptyMap()));
    }
  }
}
