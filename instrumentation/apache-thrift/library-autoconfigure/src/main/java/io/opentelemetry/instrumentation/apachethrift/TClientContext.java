package io.opentelemetry.instrumentation.apachethrift;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.INSTRUMENTATION_NAME_CLIENT;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.TAG_ARGS;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.TAG_METHOD;

import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;

@SuppressWarnings("rawtypes")
public class TClientContext extends AbstractContext {

  private static final long serialVersionUID = -2126788453042003420L;

  public TClientContext(String methodName, TBase tb) {
    if (tb != null) {
      put(TAG_ARGS, getArguments(methodName, tb));
    }
    put(TAG_METHOD, methodName);
  }

  private static String getArguments(String method, TBase base) {
    int idx = 0;
    StringBuilder buffer = new StringBuilder(method).append("(");
    while (true) {
      TFieldIdEnum field = base.fieldForId(++idx);
      if (field == null) {
        idx--;
        break;
      }
      buffer.append(field.getFieldName()).append(", ");
    }
    if (idx > 0) {
      buffer.delete(buffer.length() - 2, buffer.length());
    }
    return buffer.append(")").toString();
  }

  @Override
  public String getArguments() {
    return get(TAG_ARGS);
  }

  @Override
  public String getOperatorName() {
    return get(TAG_METHOD);
  }

  @Override
  public String getSpanType() {
    return INSTRUMENTATION_NAME_CLIENT;
  }
}
