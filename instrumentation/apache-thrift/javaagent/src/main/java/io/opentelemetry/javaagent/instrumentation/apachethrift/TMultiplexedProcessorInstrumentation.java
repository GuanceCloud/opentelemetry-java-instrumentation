package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.instrumentation.apachethrift.ServerInProtocolWrapper;
import io.opentelemetry.instrumentation.apachethrift.ThriftConstants;
import io.opentelemetry.instrumentation.apachethrift.ThriftContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;

import java.util.HashMap;
import java.util.Map;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.TM_M;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_MULTIPLEXED_PROCESSOR;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class TMultiplexedProcessorInstrumentation implements TypeInstrumentation {


  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(T_MULTIPLEXED_PROCESSOR);
  }

  @Override
  public void transform(TypeTransformer transformer) {

    transformer.applyAdviceToMethod(isConstructor()
        , getClass().getName() + "$TMultiplexedProcessorConstructorAdvice");

    transformer.applyAdviceToMethod(isMethod()
            .and(isPublic())
            .and(named("process"))
        , getClass().getName() + "$TMultiplexedProcessorProcessAdvice");
    transformer.applyAdviceToMethod(isMethod()
            .and(isPublic())
            .and(named("registerProcessor"))
        , getClass().getName() + "$TMultiplexedProcessorRegisterProcessAdvice");
    //0.12以上版本
    transformer.applyAdviceToMethod(isMethod()
            .and(isPublic())
            .and(named("registerDefault"))
        , getClass().getName() + "$TMultiplexedProcessorRegisterDefaultAdvice");
  }

  @SuppressWarnings("rawtypes")
  public static class TMultiplexedProcessorConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(@Advice.This TMultiplexedProcessor processor) {
      TM_M.put(processor,new HashMap<String, ProcessFunction>());
    }
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  public static class TMultiplexedProcessorProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This TMultiplexedProcessor processor,
        @Advice.AllArguments Object[] args) {
      TProtocol protocol = (TProtocol) args[0];
      ((ServerInProtocolWrapper) protocol).initial(new ThriftContext(TM_M.get(processor)),currentContext());
    }
  }

  @SuppressWarnings("rawtypes")
  public static class TMultiplexedProcessorRegisterProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This TMultiplexedProcessor obj,
        @Advice.AllArguments Object[] allArguments) {
      Map<String, ProcessFunction> processMap = TM_M.get(obj);
      String serviceName = (String) allArguments[0];
      TProcessor processor = (TProcessor) allArguments[1];
      processMap.putAll(ThriftConstants.getProcessMap(serviceName, processor));
      TM_M.put(obj,processMap);
    }
  }

  @SuppressWarnings("rawtypes")
  public static class TMultiplexedProcessorRegisterDefaultAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This TMultiplexedProcessor obj,
        @Advice.AllArguments Object[] allArguments) {
      Map<String, ProcessFunction> processMap = TM_M.get(obj);
      TProcessor processor = (TProcessor) allArguments[0];
      processMap.putAll(ThriftConstants.getProcessMap(processor));
      TM_M.put(obj,processMap);
    }
  }
}
