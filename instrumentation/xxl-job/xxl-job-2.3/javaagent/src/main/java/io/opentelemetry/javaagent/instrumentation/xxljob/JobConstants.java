package io.opentelemetry.javaagent.instrumentation.xxljob;

public final class JobConstants {
  public static final String INSTRUMENTATION_NAME = "xxl-job";

  public static final String JOB_PARAM = "job.param";

  public static final String JOB_TYPE = "job.type";

  public static final String JOB_METHOD = "job.method";

  public static final String JOB_ID = "job.id";

  public static final String CMD = "job.cmd";
  public static final String JOB_CODE = "job.code";

  public static final String SIMPLE_JOB = "simple-job";
  public static final String GLUE_JOB = "glue-job";
  public static final String METHOD_JOB = "method-job";
  public static final String SCRIPT_JOB = "script-job";

  public static final String HANDLER_CLASS  = "com.xxl.job.core.handler.IJobHandler";
  public static final String METHOD_CLASS   = "com.xxl.job.core.handler.impl.MethodJobHandler";
  public static final String SCRIPT_CLASS   = "com.xxl.job.core.handler.impl.ScriptJobHandler";
  public static final String GLUE_CLASS     = "com.xxl.job.core.handler.impl.GlueJobHandler";

  public static final String SPAN_NAME = "xxl-job";

  private JobConstants(){

  }
}
