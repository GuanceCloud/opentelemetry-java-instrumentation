package io.opentelemetry.javaagent.instrumentation.xxljob;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.GLUE_JOB;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_PARAM;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_TYPE;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.end;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.instrumenter;

import com.xxl.job.core.handler.IJobHandler;
import io.netty.util.internal.StringUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class GlueJobAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void execute(@Advice.This IJobHandler jobHandler,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    String operationName = GLUE_JOB;

    Context parentContext = currentContext();
    if (!instrumenter().shouldStart(parentContext, null)) {
      return;
    }
    context = instrumenter().start(parentContext, null);
    scope = context.makeCurrent();

    Span span = Span.fromContext(context);

    span.setAttribute(JOB_TYPE, GLUE_JOB);
    String jobParam = com.xxl.job.core.context.XxlJobHelper.getJobParam();
    if (!StringUtil.isNullOrEmpty(jobParam)){
      span.setAttribute(JOB_PARAM, jobParam);
    }
    span.updateName(operationName);
    System.out.println("glue job enter");
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(@Advice.Thrown Throwable throwable,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelContext") Context context) {
    if (scope != null) {
      scope.close();
    }
    System.out.println("glue job exit");
    end(context,throwable);
  }
}
