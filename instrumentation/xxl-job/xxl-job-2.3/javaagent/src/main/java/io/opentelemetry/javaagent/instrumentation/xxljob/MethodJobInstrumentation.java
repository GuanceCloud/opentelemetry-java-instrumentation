package io.opentelemetry.javaagent.instrumentation.xxljob;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_ID;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_METHOD;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_PARAM;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_TYPE;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.METHOD_CLASS;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.METHOD_JOB;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.end;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.xxl.job.core.handler.IJobHandler;
import io.netty.util.internal.StringUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodJobInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(METHOD_CLASS);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(nameStartsWith("execute"))
            .and(takesArguments(0)),
        this.getClass().getName() + "$MethodJobAdvice");
  }

  @SuppressWarnings("unused")
  public static class MethodJobAdvice{
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void execute(@Advice.This IJobHandler jobHandler,
        @Advice.FieldValue("method") Method method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope
        ) {
      System.out.println("method job enter");



      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, null)) {
        return;
      }
      context = instrumenter().start(parentContext, null);
      scope = context.makeCurrent();

      Span span = Span.fromContext(context);

      String methodName = method.getDeclaringClass().getName() + "." + method.getName();
      span.setAttribute(JOB_METHOD,methodName);
      span.setAttribute(JOB_TYPE,METHOD_JOB);
      String jobParam = com.xxl.job.core.context.XxlJobHelper.getJobParam();
      if (!StringUtil.isNullOrEmpty(jobParam)){
        span.setAttribute(JOB_PARAM, jobParam);
      }
      span.setAttribute(JOB_ID, com.xxl.job.core.context.XxlJobHelper.getJobId());

      span.updateName(methodName);

    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Thrown Throwable throwable,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelContext") Context context) {
      if (scope != null) {
        scope.close();
      }
      System.out.println("method job exit");
      end(context,throwable);
    }
  }

}
