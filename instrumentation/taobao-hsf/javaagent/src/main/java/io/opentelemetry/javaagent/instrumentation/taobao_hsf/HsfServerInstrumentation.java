package io.opentelemetry.javaagent.instrumentation.taobao_hsf;

import com.taobao.hsf.context.RPCContext;
import com.taobao.hsf.invocation.Invocation;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.taobao_hsf.HsfRequest;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.opentelemetry.instrumentation.taobao_hsf.HsfSingletons.serverInstrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class HsfServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.taobao.hsf.common.filter.CommonServerFilter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
//    RPCFilter
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("invoke"))
            .and(takesArgument(1, named("com.taobao.hsf.invocation.Invocation"))),
        this.getClass().getName() + "$ServerInvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServerInvokeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void before(@Advice.This Object filter,
        @Advice.Argument(1) Invocation invocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("hsfRequest")HsfRequest request
    ) {
      Context parentContext = currentContext();
      request = HsfRequest.create(RPCContext.getServerContext(),invocation,false);
      context = serverInstrumenter().start(parentContext,request);
      Scope scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(@Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("hsfRequest")HsfRequest request) {
      serverInstrumenter().end(currentContext(), request, null, throwable);
    }
  }

}
