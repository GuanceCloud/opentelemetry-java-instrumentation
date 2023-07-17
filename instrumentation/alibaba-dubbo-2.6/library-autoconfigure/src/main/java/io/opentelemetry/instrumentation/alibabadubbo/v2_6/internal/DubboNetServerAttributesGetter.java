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

/*  @Nullable
  @Override
  public String getServerAddress(DubboRequest request) {
    if (request.remoteAddress()!= null){
      return request.remoteAddress().getHostName();
    }
   return "";
  }*/


  @Override
  @Nullable
  public InetSocketAddress getClientInetSocketAddress(
      DubboRequest request, @Nullable Result status) {
    return request.localAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      DubboRequest request, @Nullable Result status) {
    // TODO: later version introduces TRANSPORT_ATTR_LOCAL_ADDR, might be a good idea to use it
    return request.remoteAddress();
  }

/*
  @Override
  @Nullable
  public String getTransport(DubboRequest request) {
    return null;
  }
*/

/*
  @Nullable
  @Override
  public String getHostName(DubboRequest request) {
    return null;
  }
*/

/*  @Nullable
  @Override
  public Integer getHostPort(DubboRequest request) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getPeerSocketAddress(DubboRequest request) {
    return request.remoteAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getHostSocketAddress(DubboRequest request) {
    return request.localAddress();
  }*/
}
