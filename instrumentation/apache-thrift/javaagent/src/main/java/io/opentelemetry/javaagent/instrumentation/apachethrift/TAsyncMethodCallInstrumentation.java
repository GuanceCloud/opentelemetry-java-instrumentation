package io.opentelemetry.javaagent.instrumentation.apachethrift;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.T_ASYNC_METHOD_CALL;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class TAsyncMethodCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(T_ASYNC_METHOD_CALL));
  }

  @Override
  public void transform(TypeTransformer transformer) {

    transformer.applyAdviceToMethod(isConstructor()
        ,this.getClass().getPackage().getName()+ ".AsyncMethodCallConstructorAdvice");
    transformer.applyAdviceToMethod(isMethod()
            .and(isProtected())
            .and(named("prepareMethodCall"))
        ,this.getClass().getPackage().getName() + ".AsyncMethodCallMethodAdvice");
  }

}
