/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0.SpringAiAlibabaAgentSingletons.toolInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AgentToolNodeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.alibaba.cloud.ai.graph.agent.node.AgentToolNode");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("executeToolCallWithInterceptors")
            .and(isPrivate())
            .and(takesArguments(7))
            .and(
                takesArgument(
                    0, named("org.springframework.ai.chat.messages.AssistantMessage$ToolCall")))
            .and(takesArgument(4, boolean.class))
            .and(
                returns(
                    named("com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse"))),
        getClass().getName() + "$ToolCallAdvice");
  }

  @SuppressWarnings("unused")
  public static class ToolCallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Context onEnter(
        @Advice.FieldValue("agentName") String agentName,
        @Advice.Argument(0) Object toolCall,
        @Advice.Argument(4) boolean parallelExecution) {
      ToolRequest request = new ToolRequest(agentName, toolCall, parallelExecution);
      Context parentContext = Context.current();
      if (!toolInstrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      return toolInstrumenter().start(parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Context context,
        @Advice.FieldValue("agentName") String agentName,
        @Advice.Argument(0) Object toolCall,
        @Advice.Argument(4) boolean parallelExecution,
        @Advice.Return ToolCallResponse response,
        @Advice.Thrown Throwable error) {
      if (context != null) {
        ToolRequest request = new ToolRequest(agentName, toolCall, parallelExecution);
        toolInstrumenter().end(context, request, response, error);
      }
    }
  }
}
