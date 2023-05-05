/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;

@Activate(group = {"consumer", "provider"})
public final class OpenTelemetryFilter implements Filter {

  private final Filter delegate;

  public OpenTelemetryFilter() {
    delegate = DubboTelemetry.create(GlobalOpenTelemetry.get()).newFilter();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    return delegate.invoke(invoker, invocation);
  }
}
