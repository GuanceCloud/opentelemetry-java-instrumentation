package io.opentelemetry.javaagent.instrumentation.xxljob;

import com.xxl.job.core.handler.IJobHandler;
import io.netty.util.internal.StringUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_ID;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_PARAM;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_TYPE;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.SIMPLE_JOB;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.end;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.instrumenter;

@SuppressWarnings("unused")
public class SimpleJobAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void execute(@Advice.This IJobHandler jobHandler,
      @Advice.FieldValue("jobId") int jobId,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    String operationName = SIMPLE_JOB+"/id/"+jobId;

    Context parentContext = currentContext();
    if (!instrumenter().shouldStart(parentContext, null)) {
      return;
    }
    context = instrumenter().start(parentContext, null);
    scope = context.makeCurrent();

    Span span = Span.fromContext(context);

    span.setAttribute(JOB_TYPE, SIMPLE_JOB);
    span.setAttribute(JOB_ID, jobId);
    String jobParam = com.xxl.job.core.context.XxlJobHelper.getJobParam();
    if (!StringUtil.isNullOrEmpty(jobParam)){
      span.setAttribute(JOB_PARAM, jobParam);
    }
    span.updateName(operationName);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(@Advice.Thrown Throwable throwable,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelContext") Context context) {
    if (scope != null) {
      scope.close();
    }
    end(context,throwable);
  }
}
