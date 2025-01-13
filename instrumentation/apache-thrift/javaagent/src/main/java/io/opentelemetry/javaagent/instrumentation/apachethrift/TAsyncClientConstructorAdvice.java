package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.instrumentation.apachethrift.CtProtocolFactory;
import io.opentelemetry.instrumentation.apachethrift.ThriftConstants;
import net.bytebuddy.asm.Advice;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocolFactory;

public class TAsyncClientConstructorAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void exit(@Advice.This TAsyncClient tAsyncClient
      , @Advice.FieldValue("___protocolFactory") TProtocolFactory protocolFactory
  ) throws NoSuchFieldException, IllegalAccessException {
    ThriftConstants.setValue(
        TAsyncClient.class,
        tAsyncClient,
        "___protocolFactory",
        new CtProtocolFactory(protocolFactory)
    );
  }
}
