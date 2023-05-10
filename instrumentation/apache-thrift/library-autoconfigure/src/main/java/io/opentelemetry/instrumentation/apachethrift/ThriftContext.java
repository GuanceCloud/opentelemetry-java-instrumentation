package io.opentelemetry.instrumentation.apachethrift;

import java.util.Map;
import org.apache.thrift.ProcessFunction;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.INSTRUMENTATION_NAME_SERVER;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.TAG_ARGS;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.TAG_METHOD;

@SuppressWarnings("rawtypes")
public class ThriftContext extends AbstractContext {
  private static final long serialVersionUID = -2818758873846404694L;
  private final Map<String, ProcessFunction> processMapView;

  public ThriftContext(Map<String, ProcessFunction> processMapView) {
    this.processMapView = processMapView;
  }

  @Override
  public String getSpanType() {
    return INSTRUMENTATION_NAME_SERVER;
  }

  @Override
  public String getArguments() {
    String m = get(TAG_ARGS);
    if (m!=null){
      return m;
    }
    if (processMapView==null){
      return null;
    }
    ProcessFunction function = processMapView.get(methodName);
    if (function==null){
      return null;
    }
    return function.getEmptyArgsInstance().toString();
  }

  @Override
  public String getOperatorName() {
    String m = get(TAG_METHOD);
    if (m!=null){
      return m;
    }
    if (processMapView==null){
      return null;
    }
    ProcessFunction function = processMapView.get(methodName);
    if (function==null){
      return methodName;
    }
    return function.getClass().getName();
  }

}
