package io.opentelemetry.javaagent.instrumentation.apachethrift;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_ASYNC_PROCESSOR;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_BASE_ASYNC_PROCESSOR;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_BASE_PROCESSOR;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_MULTIPLEXED_PROCESSOR;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_PROCESSOR;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(namedOneOf(T_PROCESSOR,T_ASYNC_PROCESSOR))
        .and(not(named(T_BASE_ASYNC_PROCESSOR)))
        .and(not(named(T_BASE_PROCESSOR)))
        .and(not(named(T_MULTIPLEXED_PROCESSOR)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isMethod()
            .and(isPublic())
            .and(named("process"))
        , this.getClass().getPackage().getName() + ".TProcessorProcessAdvice");

  }

}
