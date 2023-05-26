package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.apachethrift.ServerInProtocolWrapper;
import io.opentelemetry.instrumentation.apachethrift.ThriftContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TBaseProcessor;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_BASE_PROCESSOR;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.serverInstrumenter;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class TBaseProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(T_BASE_PROCESSOR));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isMethod()
            .and(isPublic())
            .and(named("process"))
        , getClass().getName() + "$ProcessAdvice");
  }

  @SuppressWarnings({"unchecked","rawtypes"})
  public static class ProcessAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void before(@Advice.This Object obj
        , @Advice.AllArguments Object[] args) {
      if (obj instanceof TBaseProcessor) {
        Object in = args[0];
        if (in instanceof ServerInProtocolWrapper) {
          TBaseProcessor tBaseProcessor = (TBaseProcessor) obj;
          ((ServerInProtocolWrapper) in).initial(new ThriftContext(tBaseProcessor.getProcessMapView()),Context.current());
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void after(@Advice.Thrown Throwable throwable) {
       Context context = Context.current();
       if (context!=null) {
         serverInstrumenter().end(Context.current(), CONTEXT_THREAD.get(), null, throwable);
         CONTEXT_THREAD.remove();
       }
      //todo end
    }
  }
}
