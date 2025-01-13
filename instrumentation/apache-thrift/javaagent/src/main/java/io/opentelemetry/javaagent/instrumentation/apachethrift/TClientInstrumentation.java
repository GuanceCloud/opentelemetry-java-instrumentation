package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.apachethrift.AbstractContext;
import io.opentelemetry.instrumentation.apachethrift.TClientContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TBase;
import org.apache.thrift.TServiceClient;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CLIENT_INJECT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.TSERVICE_CLIENT;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.clientInstrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(TSERVICE_CLIENT));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor()
            .and(takesArgument(1, named("org.apache.thrift.protocol.TProtocol")))
        , this.getClass().getPackage().getName() + ".TClientConstructorAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPrivate())
            .and(named("sendBase"))
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("org.apache.thrift.TBase"))),
        getClass().getName() + "$SendBaseAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
//            .and(takesArgument(1,String.class))
            .and(named("receiveBase")),
        getClass().getName() + "$ReceiveBaseAdvice");
  }


  @SuppressWarnings({"rawtypes","unused"})
  public static class SendBaseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This TServiceClient tServiceClient,
                                     @Advice.Argument(0) String methodName,
                                     @Advice.Argument(1) TBase tb,
        @Advice.Local("otelContext") Context context
        ) {
      System.out.println("SendBaseAdvice");
      Context parentContext = currentContext();
      AbstractContext request = new TClientContext(methodName,tb);
//      if (!clientInstrumenter().shouldStart(parentContext, request)) {
//        return;
//      }
      context = clientInstrumenter().start(parentContext, request);
      Scope scope = context.makeCurrent();
      Span span = Span.fromContext(context);
      CONTEXT_THREAD.set(request);
      System.out.println(span.getSpanContext().getTraceId()+"\t"+span.getSpanContext().getSpanId());
    }
  }

  public static class ReceiveBaseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable) {
      clientInstrumenter().end(currentContext(), CONTEXT_THREAD.get(), null, throwable);
      CLIENT_INJECT_THREAD.remove();
      CONTEXT_THREAD.remove();
    }
  }
}
