package io.opentelemetry.instrumentation.apachethrift;


import io.opentelemetry.context.Context;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CLIENT_INJECT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.clientInstrumenter;

@SuppressWarnings("cast")
public class ThriftAsyncMethodCallback<T> implements AsyncMethodCallback<T> {
  public static final Logger logger = LoggerFactory.getLogger(ThriftAsyncMethodCallback.class);
  final AsyncMethodCallback<T> callback;
  Context context;

  public ThriftAsyncMethodCallback(AsyncMethodCallback<T> callback, Context context) {
    this.callback = callback;
    this.context = context;
  }

  @Override
  public void onComplete(T response) {
    if (context==null){
      return;
    }
    try {
      logger.debug("onComplete scope is not null,thread:" + Thread.currentThread().getName());
      clientInstrumenter().end(context, CONTEXT_THREAD.get(), null, null);
      CLIENT_INJECT_THREAD.remove();
    } finally {
      callback.onComplete(response);
    }
  }

  @Override
  public void onError(Exception exception) {
    if (context==null){
      return;
    }
    try {
      clientInstrumenter().end(context, CONTEXT_THREAD.get(), null, exception);
      CLIENT_INJECT_THREAD.remove();
    } finally {
      callback.onError(exception);
    }
  }
}
