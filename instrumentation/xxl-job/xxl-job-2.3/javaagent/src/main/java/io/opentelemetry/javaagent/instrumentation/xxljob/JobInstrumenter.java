package io.opentelemetry.javaagent.instrumentation.xxljob;

import com.xxl.job.core.context.XxlJobContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.INSTRUMENTATION_NAME;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.JOB_CODE;
import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.SPAN_NAME;

public final class JobInstrumenter {

  private static final Instrumenter<Void, Void> INSTRUMENTER =
      Instrumenter.<Void, Void>builder(
              GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, s -> SPAN_NAME)
          .buildInstrumenter();

  public static Instrumenter<Void, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void end(Context context,Throwable throwable){
    Span span = Span.fromContext(context);
    span.setAttribute(JOB_CODE, XxlJobContext.getXxlJobContext().getHandleCode());
    if (XxlJobContext.getXxlJobContext().getHandleCode() > XxlJobContext.HANDLE_COCE_SUCCESS) {
      throwable = new Throwable(XxlJobContext.getXxlJobContext().getHandleMsg());
    }
    instrumenter().end(context, null, null, throwable);
  }

  private JobInstrumenter(){

  }
}
