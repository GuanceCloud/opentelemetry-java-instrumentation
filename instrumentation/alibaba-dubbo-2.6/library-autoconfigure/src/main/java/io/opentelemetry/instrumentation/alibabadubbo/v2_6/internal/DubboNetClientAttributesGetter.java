/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6.internal;
import com.alibaba.dubbo.rpc.Result;
import io.opentelemetry.instrumentation.alibabadubbo.v2_6.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNetClientAttributesGetter
    implements NetClientAttributesGetter<DubboRequest, Result> {

/*
  @Override
  @Nullable
  public String getTransport(DubboRequest request, @Nullable Result response) {
    return null;
  }
*/

  @Nullable
  @Override
  public String getServerAddress(DubboRequest request) {
    return  request.url().getHost();
  }

/*  @Nullable
  @Override
  public String getPeerName(DubboRequest request) {
    return request.url().getHost();
  }*/

  @Nullable
  @Override
  public Integer getServerPort(DubboRequest request) {
    return request.url().getPort();
  }

  @Override
  @Nullable
  public InetSocketAddress getServerInetSocketAddress(
      DubboRequest request, @Nullable Result response) {
    return request.remoteAddress();
  }
}
