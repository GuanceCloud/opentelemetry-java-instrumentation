/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

@AutoValue
public abstract class DubboRequest {

  static DubboRequest create(RpcInvocation invocation, RpcContext context) {
    // In dubbo 3 RpcContext delegates to a ThreadLocal context. We copy the url and remote address
    // here to ensure we can access them from the thread that ends the span.
    return new AutoValue_DubboRequest(
        invocation,
        context,
        context.getUrl(),
        context.getRemoteAddress(),
        context.getLocalAddress());
  }

  abstract RpcInvocation invocation();

  public abstract RpcContext context();

  public abstract URL url();

  @Nullable
  public abstract InetSocketAddress remoteAddress();

  @Nullable
  public abstract InetSocketAddress localAddress();
}
