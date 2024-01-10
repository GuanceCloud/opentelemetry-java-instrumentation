/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadubbo.v2_6.internal;

import com.alibaba.dubbo.rpc.Result;
import io.opentelemetry.instrumentation.alibabadubbo.v2_6.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNetServerAttributesGetter
    implements ServerAttributesGetter<DubboRequest,Result> {

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      DubboRequest request, @Nullable Result result) {
    return request.localAddress();
  }
}
