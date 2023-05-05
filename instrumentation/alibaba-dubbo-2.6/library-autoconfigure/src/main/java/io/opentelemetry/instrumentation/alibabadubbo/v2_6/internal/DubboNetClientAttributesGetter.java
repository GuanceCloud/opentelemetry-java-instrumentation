/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6.internal;

import com.alibaba.dubbo.rpc.Result;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.instrumentation.alibabadubbo.v2_6.DubboRequest;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<DubboRequest, Result> {

  @Override
  @Nullable
  public String getTransport(DubboRequest request, @Nullable Result response) {
    return null;
  }

  @Nullable
  @Override
  public String getPeerName(DubboRequest request) {
    return request.url().getHost();
  }

  @Override
  public Integer getPeerPort(DubboRequest request) {
    return request.url().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
          DubboRequest request, @Nullable Result response) {
    return request.remoteAddress();
  }
}
