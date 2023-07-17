/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6.internal;

import com.alibaba.dubbo.rpc.Result;
import io.opentelemetry.instrumentation.alibabadubbo.v2_6.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNetServerAttributesGetter
    implements NetServerAttributesGetter<DubboRequest,Result> {

  @Nullable
  @Override
  public String getServerAddress(DubboRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(DubboRequest request) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getClientInetSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.remoteAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.localAddress();
  }
}
