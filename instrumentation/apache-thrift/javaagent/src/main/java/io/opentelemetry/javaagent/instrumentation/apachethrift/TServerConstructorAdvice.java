package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.instrumentation.apachethrift.StProtocolFactory;
import io.opentelemetry.instrumentation.apachethrift.ThriftConstants;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TServerConstructorAdvice {
  private static final Logger logger = LoggerFactory.getLogger(TServerConstructorAdvice.class);

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onExit(@Advice.This(typing = Assigner.Typing.DYNAMIC) TServer tServer,
                            @Advice.FieldValue(value = "inputProtocolFactory_", readOnly = false, typing = Assigner.Typing.DYNAMIC) TProtocolFactory inputProtocolFactory
  ) {
    try {
      TProtocolFactory trans = new StProtocolFactory(inputProtocolFactory);
      ThriftConstants.setValue(
          TServer.class,
          tServer,
          "inputProtocolFactory_",
          trans
      );
    } catch (Exception e) {
      logger.debug("TServerConstructorAdvice exception:", e);
    }
  }
}
