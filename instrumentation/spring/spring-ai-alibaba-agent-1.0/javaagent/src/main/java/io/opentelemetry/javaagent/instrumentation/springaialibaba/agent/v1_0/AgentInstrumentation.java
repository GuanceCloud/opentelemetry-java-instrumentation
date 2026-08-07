/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0.SpringAiAlibabaAgentSingletons.agentInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.alibaba.cloud.ai.graph.agent.Agent;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Flux;

class AgentInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.alibaba.cloud.ai.graph.agent.Agent");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("doInvoke", "doInvokeAndGetOutput")
            .and(takesArguments(2))
            .and(takesArgument(0, named("java.util.Map")))
            .and(returns(named("java.util.Optional"))),
        getClass().getName() + "$InvokeAdvice");
    transformer.applyAdviceToMethod(
        named("doStream")
            .and(takesArguments(2))
            .and(takesArgument(0, named("java.util.Map")))
            .and(returns(named("reactor.core.publisher.Flux"))),
        getClass().getName() + "$StreamAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {
    public static class AdviceScope {
      private final AgentRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(AgentRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(AgentRequest request) {
        Context parentContext = Context.current();
        if (!agentInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = agentInstrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable error) {
        scope.close();
        agentInstrumenter().end(context, request, null, error);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(@Advice.This Agent agent) {
      return AdviceScope.start(new AgentRequest(agent.name(), "invoke"));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable AdviceScope scope, @Advice.Thrown @Nullable Throwable error) {
      if (scope != null) {
        scope.end(error);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class StreamAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Flux<?> onExit(
        @Advice.This Agent agent,
        @Advice.Return Flux<?> publisher) {
      if (publisher == null) {
        return null;
      }
      return AgentStreamTracing.wrap(publisher, new AgentRequest(agent.name(), "stream"));
    }
  }
}
