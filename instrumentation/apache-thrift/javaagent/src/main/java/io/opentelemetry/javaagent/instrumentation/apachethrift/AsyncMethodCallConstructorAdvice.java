package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.instrumentation.apachethrift.ThriftAsyncMethodCallback;
import io.opentelemetry.instrumentation.apachethrift.ThriftConstants;
import net.bytebuddy.asm.Advice;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncMethodCallConstructorAdvice {
  private static final Logger logger = LoggerFactory.getLogger(AsyncMethodCallConstructorAdvice.class);

  @SuppressWarnings({"rawtypes","unchecked"})
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void after(@Advice.This TAsyncMethodCall objInst
      , @Advice.AllArguments Object[] args) {
    if (args[3] instanceof AsyncMethodCallback) {
      AsyncMethodCallback<Object> callback = (AsyncMethodCallback) args[3];
      try {
        ThriftConstants.setValue(TAsyncMethodCall.class, objInst, "callback", new ThriftAsyncMethodCallback<Object>(callback,null));
      } catch (Exception e) {
        logger.error("set value error:", e);
      }
    }
  }
}
