package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.instrumentation.apachethrift.AsyncContext;
import io.opentelemetry.instrumentation.apachethrift.ServerInProtocolWrapper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TBaseAsyncProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.AbstractNonblockingServer;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_BASE_ASYNC_PROCESSOR;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.serverInstrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

@SuppressWarnings("unchecked")
public class TBaseAsyncProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(T_BASE_ASYNC_PROCESSOR));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isMethod()
            .and(isPublic())
            .and(named("process"))
        , getClass().getName() + "$AsyncProcessAdvice");
  }

  @SuppressWarnings({"unchecked","rawtypes"})
  public static class AsyncProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This TBaseAsyncProcessor tBaseAsyncProcessor
        , @Advice.AllArguments Object[] args) {
      TProtocol protocol = ((AbstractNonblockingServer.AsyncFrameBuffer) args[0]).getInputProtocol();
      ((ServerInProtocolWrapper) protocol).initial(new AsyncContext(tBaseAsyncProcessor.getProcessMapView()),currentContext());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void after(@Advice.Thrown Throwable throwable) {
      serverInstrumenter().end(currentContext(), CONTEXT_THREAD.get(), null, throwable);
      CONTEXT_THREAD.remove();
    }
  }
}
