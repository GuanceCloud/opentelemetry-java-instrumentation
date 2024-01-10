/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.ChannelUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.DatagramChannel;

enum NettyConnectHttpAttributesGetter
    implements HttpClientAttributesGetter<NettyConnectionRequest, Channel> {
  INSTANCE;

  @Nullable
  @Override
  public String getUrlFull(NettyConnectionRequest nettyConnectionRequest) {
    return null;
  }

  @Nullable
  @Override
  public String getHttpRequestMethod(NettyConnectionRequest nettyConnectionRequest) {
    return null;
  }

  @Override
  public List<String> getHttpRequestHeader(
      NettyConnectionRequest nettyConnectionRequest, String name) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public Integer getHttpResponseStatusCode(
      NettyConnectionRequest nettyConnectionRequest, Channel channel, @Nullable Throwable error) {
    return null;
  }

  @Override
  public List<String> getHttpResponseHeader(
      NettyConnectionRequest nettyConnectionRequest, Channel channel, String name) {
    return Collections.emptyList();
  }

  @Override
  public String getTransport(NettyConnectionRequest request, @Nullable Channel channel) {
    return channel instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getNetworkTransport(NettyConnectionRequest request, @Nullable Channel channel) {
    return ChannelUtil.getNetworkTransport(channel);
  }

  @Nullable
  @Override
  public String getServerAddress(NettyConnectionRequest request) {
    SocketAddress requestedAddress = request.remoteAddressOnStart();
    if (requestedAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) requestedAddress).getHostString();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(NettyConnectionRequest request) {
    SocketAddress requestedAddress = request.remoteAddressOnStart();
    if (requestedAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) requestedAddress).getPort();
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      NettyConnectionRequest request, @Nullable Channel channel) {
    if (channel == null) {
      return null;
    }
    SocketAddress remoteAddress = channel.getRemoteAddress();
    if (remoteAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) remoteAddress;
    }
    return null;
  }
}
