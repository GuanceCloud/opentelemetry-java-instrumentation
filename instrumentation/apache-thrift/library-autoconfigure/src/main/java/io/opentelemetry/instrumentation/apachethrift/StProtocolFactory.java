package io.opentelemetry.instrumentation.apachethrift;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

public class StProtocolFactory implements TProtocolFactory {
  private static final long serialVersionUID = 109860674394749646L;
  TProtocolFactory inputProtocolFactory;
  public StProtocolFactory(TProtocolFactory inputProtocolFactory){
    this.inputProtocolFactory = inputProtocolFactory;
  }
  @Override
  public TProtocol getProtocol(TTransport tTransport) {
    ServerInProtocolWrapper wrapper = new ServerInProtocolWrapper(inputProtocolFactory.getProtocol(tTransport));
    return wrapper;
  }
}
