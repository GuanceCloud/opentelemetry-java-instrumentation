package io.opentelemetry.instrumentation.apachethrift;

import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TBaseAsyncProcessor;
import org.apache.thrift.TBaseProcessor;
import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked","rawtypes"})
public class ThriftConstants {

  public static final Logger logger = LoggerFactory.getLogger(ThriftConstants.class);

  public static final String INSTRUMENTATION_NAME = "thrift";
  public static final String SPAN_NAME = "thrift";
  public static final String INSTRUMENTATION_NAME_CLIENT = "thrift-client";
  public static final String INSTRUMENTATION_NAME_SERVER = "thrift-server";
  public static final String TASYNC_CLIENT = "org.apache.thrift.async.TAsyncClient";
  public static final String T_ASYNC_METHOD_CALL = "org.apache.thrift.async.TAsyncMethodCall";
  public static final String TSERVICE_CLIENT = "org.apache.thrift.TServiceClient";
  public static final String T_BASE_PROCESSOR = "org.apache.thrift.TBaseProcessor";
  public static final String T_PROCESSOR = "org.apache.thrift.TProcessor";
  public static final String T_ASYNC_PROCESSOR = "org.apache.thrift.TAsyncProcessor";
  public static final String T_MULTIPLEXED_PROCESSOR = "org.apache.thrift.TMultiplexedProcessor";
  public static final String T_BASE_ASYNC_PROCESSOR = "org.apache.thrift.TBaseAsyncProcessor";
  public static final String T_SERVER = "org.apache.thrift.server.TServer";

  public static final String THRIFT_MAGIC_FIELD = "THRIFT_MAGIC_FIELD"; // Field Name
  public static final short THRIFT_MAGIC_FIELD_ID = 8888; // Field ID, a magic number

  public static final String THRIFT_CLIENT_COMPONENT = INSTRUMENTATION_NAME_CLIENT;
  public static final String THRIFT_SERVER_COMPONENT = INSTRUMENTATION_NAME_SERVER;

  public static final ThreadLocal<AbstractContext> CONTEXT_THREAD = new ThreadLocal<>();
  public static final ThreadLocal<Boolean> CLIENT_INJECT_THREAD = new ThreadLocal<>();
  public static final ConcurrentHashMap<TMultiplexedProcessor,Map<String, ProcessFunction>> TM_M = new ConcurrentHashMap<>();

  public static final String TAG_ARGS = "args";
  public static final String TAG_METHOD = "method";

  @SuppressWarnings("rawtypes")
  public static final void setValue(Class klass, Object instance, String name, Object value) throws NoSuchFieldException, IllegalAccessException {
    Field field = klass.getDeclaredField(name);
    field.setAccessible(true);
    field.set(instance, value);
  }

  @SuppressWarnings("rawtypes")
  public static final Object getValue(Class klass, Object instance, String name) throws NoSuchFieldException, IllegalAccessException {
    Field field = klass.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(instance);
  }

  public static Map<String, ProcessFunction> getProcessMap(String serviceName, TProcessor processor) {
    Map<String, ProcessFunction> hashMap = new HashMap<>();
    if (processor instanceof TBaseProcessor) {
      Map<String, ProcessFunction> processMapView = ((TBaseProcessor) processor).getProcessMapView();
      processMapView.forEach((k, v) -> hashMap.put(serviceName + TMultiplexedProtocol.SEPARATOR + k, v));
    } else if (processor instanceof TBaseAsyncProcessor) {
      Map<String, ProcessFunction> processMapView = ((TBaseAsyncProcessor) processor).getProcessMapView();
      processMapView.forEach((k, v) -> hashMap.put(serviceName + TMultiplexedProtocol.SEPARATOR + k, v));
    } else {

      if (logger.isDebugEnabled()) {
        logger.debug("Not support this processor:{},serviceName:{}，super:{}", serviceName, processor.getClass().getName(),processor.getClass().getSuperclass().getName());
      }
    }
    return hashMap;
  }

  public static Map<String, ProcessFunction> getProcessMap(TProcessor processor) {
    Map<String, ProcessFunction> hashMap = new HashMap<>();
    if (processor instanceof TBaseProcessor || processor instanceof TBaseAsyncProcessor) {
      Map<String, ProcessFunction> processMapView = ((TBaseProcessor) processor).getProcessMapView();
      hashMap.putAll(processMapView);
//    } else if (processor instanceof TBaseAsyncProcessor) {
//      Map<String, ProcessFunction> processMapView = ((TBaseProcessor) processor).getProcessMapView();
//      hashMap.putAll(processMapView);
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Not support this processor:{}", processor.getClass().getName());
      }
    }
    return hashMap;
  }

  private ThriftConstants(){

  }
}
