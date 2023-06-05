package io.opentelemetry.instrumentation.taobao_hsf;

import com.google.auto.value.AutoValue;
import com.taobao.hsf.context.RPCContext;
import com.taobao.hsf.invocation.Invocation;

@AutoValue
public abstract class HSFRequest{


  public abstract RPCContext rpcContext();

  public abstract Invocation invocation();

  public abstract boolean isClient();

  public static HSFRequest create(RPCContext rpcContext,Invocation invocation,boolean isClient) {
    return new AutoValue_HSFRequest(
        rpcContext,invocation,isClient);
  }


  public String getMethod(){
    return invocation().getMethodName();
  }


  public String getService(){
    if (!isClient()){
      return invocation().getServerInvocationContext().getMetadata().getUniqueName();
    }
    Invocation.ClientInvocationContext context = invocation().getClientInvocationContext();
    return context.getMethodModel().getUniqueName();
  }

}
