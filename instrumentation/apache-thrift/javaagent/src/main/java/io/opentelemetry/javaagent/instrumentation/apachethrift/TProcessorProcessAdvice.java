package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.apachethrift.ServerInProtocolWrapper;
import io.opentelemetry.instrumentation.apachethrift.ThriftContext;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.serverInstrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;

public class TProcessorProcessAdvice {
  public static final Logger logger = LoggerFactory.getLogger(TProcessorProcessAdvice.class);
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.This Object obj, @Advice.AllArguments Object[] args) {
    logger.info("TProcessorProcessAdvice : " + obj.getClass().getName());
    Object in = args[0];
    if (in instanceof ServerInProtocolWrapper) {
      ((ServerInProtocolWrapper) in).initial(new ThriftContext(null),currentContext());
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void after(@Advice.Thrown Throwable throwable) {
    Context context = Context.current();
    if (context!=null) {
      serverInstrumenter().end(Context.current(), CONTEXT_THREAD.get(), null, throwable);
      CONTEXT_THREAD.remove();
    }
  }
}
