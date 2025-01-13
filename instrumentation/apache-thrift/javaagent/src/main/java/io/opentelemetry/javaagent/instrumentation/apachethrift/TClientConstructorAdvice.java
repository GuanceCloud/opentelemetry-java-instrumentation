package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.instrumentation.apachethrift.ClientOutProtocolWrapper;
import io.opentelemetry.instrumentation.apachethrift.ThriftConstants;
import net.bytebuddy.asm.Advice;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;

public class TClientConstructorAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void exit(@Advice.This TServiceClient tServiceClient
      , @Advice.FieldValue("oprot_") TProtocol oprot
  ) throws NoSuchFieldException, IllegalAccessException {
    if (!(oprot instanceof ClientOutProtocolWrapper)) {
      ThriftConstants.setValue(
          TServiceClient.class,
          tServiceClient,
          "oprot_",
          new ClientOutProtocolWrapper(oprot)
      );
    }
  }
}
