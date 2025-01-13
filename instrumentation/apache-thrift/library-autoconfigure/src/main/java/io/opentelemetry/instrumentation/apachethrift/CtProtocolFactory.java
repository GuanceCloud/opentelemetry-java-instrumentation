package io.opentelemetry.instrumentation.apachethrift;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

public class CtProtocolFactory implements TProtocolFactory {

  private static final long serialVersionUID = 7848944258988155246L;
  TProtocolFactory inputProtocolFactory;
  public CtProtocolFactory(TProtocolFactory inputProtocolFactory){
    this.inputProtocolFactory = inputProtocolFactory;
  }
  @Override
  public TProtocol getProtocol(TTransport tTransport) {
    ClientOutProtocolWrapper wrapper = new ClientOutProtocolWrapper(inputProtocolFactory.getProtocol(tTransport));
    return wrapper;
  }
}
