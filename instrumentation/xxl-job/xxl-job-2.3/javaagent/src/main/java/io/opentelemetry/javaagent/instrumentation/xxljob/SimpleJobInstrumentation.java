package io.opentelemetry.javaagent.instrumentation.xxljob;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.GLUE_CLASS;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.HANDLER_CLASS;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.METHOD_CLASS;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.SCRIPT_CLASS;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SimpleJobInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named(HANDLER_CLASS))
        .and(not(named(METHOD_CLASS)))
        .and(not(named(SCRIPT_CLASS)))
        .and(not(named(GLUE_CLASS)))
        ;
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(nameStartsWith("execute"))
            .and(takesArguments(0)),
        this.getClass().getPackage().getName()+ ".SimpleJobAdvice");
  }
}
