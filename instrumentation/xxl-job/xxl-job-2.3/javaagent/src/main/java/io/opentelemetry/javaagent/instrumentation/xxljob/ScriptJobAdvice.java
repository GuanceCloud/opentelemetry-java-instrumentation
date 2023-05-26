package io.opentelemetry.javaagent.instrumentation.xxljob;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.CMD;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_ID;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_PARAM;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_TYPE;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.SCRIPT_JOB;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.end;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobInstrumenter.instrumenter;

import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import io.netty.util.internal.StringUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class ScriptJobAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void execute(@Advice.This IJobHandler jobHandler
      , @Advice.FieldValue("jobId") int jobId,
      @Advice.FieldValue("glueType") GlueTypeEnum glueType,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    String operationName = glueType.getCmd() + "/id/" + jobId;


    Context parentContext = currentContext();
    if (!instrumenter().shouldStart(parentContext, null)) {
      return;
    }
    context = instrumenter().start(parentContext, null);
    scope = context.makeCurrent();

    Span span = Span.fromContext(context);

    span.setAttribute(JOB_TYPE, SCRIPT_JOB);
    span.setAttribute(JOB_ID, jobId);
    span.setAttribute(CMD, glueType.getCmd());
    String jobParam = com.xxl.job.core.context.XxlJobHelper.getJobParam();
    if (!StringUtil.isNullOrEmpty(jobParam)){
      span.setAttribute(JOB_PARAM, jobParam);
    }
    span.updateName(operationName);
    System.out.println("ScriptJobAdvice job enter");
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(@Advice.Thrown Throwable throwable,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Local("otelContext") Context context) {
    if (scope != null) {
      scope.close();
    }
    System.out.println("ScriptJobAdvice job exit");
    end(context,throwable);
  }

}
