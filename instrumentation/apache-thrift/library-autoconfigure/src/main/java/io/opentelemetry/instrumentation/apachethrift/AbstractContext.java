package io.opentelemetry.instrumentation.apachethrift;

import java.util.HashMap;

import static java.util.concurrent.TimeUnit.MILLISECONDS;


public abstract class AbstractContext extends HashMap<String,String> {
  private static final long serialVersionUID = -6872498296375172854L;
  public String methodName;
  public long startTime = 0L;
  public boolean createdSpan = false;

  public abstract String getSpanType();

  public abstract String getArguments();

  public abstract String getOperatorName();

  public final void setup(String methodName) {
    this.methodName = methodName;
    this.startTime = MILLISECONDS.toMicros(System.currentTimeMillis());
  }

  public boolean isCreatedSpan() {
    return createdSpan;
  }

  public void setCreatedSpan(boolean createdSpan) {
    this.createdSpan = createdSpan;
  }
}
