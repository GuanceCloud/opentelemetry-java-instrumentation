/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;

public final class AgentStreamTracing {
  public static <T> Flux<T> wrap(Flux<T> source, AgentRequest request) {
    return Flux.defer(
        () -> {
          Instrumenter<AgentRequest, Void> instrumenter =
              SpringAiAlibabaAgentSingletons.agentInstrumenter();
          Context parentContext = Context.current();
          if (!instrumenter.shouldStart(parentContext, request)) {
            return source;
          }
          Context context = instrumenter.start(parentContext, request);
          AtomicBoolean ended = new AtomicBoolean();
          Flux<T> traced =
              source
                  .doOnError(error -> end(instrumenter, context, request, error, ended))
                  .doOnComplete(() -> end(instrumenter, context, request, null, ended))
                  .doOnCancel(() -> end(instrumenter, context, request, null, ended));
          return ContextPropagationOperator.runWithContext(traced, context);
        });
  }

  private static void end(
      Instrumenter<AgentRequest, Void> instrumenter,
      Context context,
      AgentRequest request,
      Throwable error,
      AtomicBoolean ended) {
    if (ended.compareAndSet(false, true)) {
      instrumenter.end(context, request, null, error);
    }
  }

  private AgentStreamTracing() {}
}
