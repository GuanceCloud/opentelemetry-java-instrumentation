package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.apachethrift.TClientContext;
import io.opentelemetry.instrumentation.apachethrift.ThriftAsyncMethodCallback;
import io.opentelemetry.instrumentation.apachethrift.ThriftConstants;
import net.bytebuddy.asm.Advice;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.clientInstrumenter;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;


public class AsyncMethodCallMethodAdvice {
  public static final Logger logger = LoggerFactory.getLogger(AsyncMethodCallMethodAdvice.class);

  @SuppressWarnings({"unchecked","rawtypes","unused"})
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.This TAsyncMethodCall methodCall,
                                   @Advice.AllArguments Object[] args,
                                   @Advice.FieldValue("callback") AsyncMethodCallback<Object> callback) {
    Context parentContext = currentContext();
    TClientContext request = new TClientContext(methodCall.getClass().getName(),null);
    if (!clientInstrumenter().shouldStart(parentContext, request)) {
      return;
    }
    Context context = clientInstrumenter().start(parentContext, request);
    Scope scope = context.makeCurrent();
    Span span = Span.fromContext(context);
    CONTEXT_THREAD.set(request);
    try {
      ThriftConstants.setValue(TAsyncMethodCall.class, methodCall, "callback", new ThriftAsyncMethodCallback<Object>(callback, context));
    } catch (Exception e) {
      if (logger.isDebugEnabled()){
        logger.debug("set value callback fail",e);
      }
      logger.error("set value callback fail",e);
    }
  }
}
