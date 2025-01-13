package io.opentelemetry.instrumentation.apachethrift;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CLIENT_INJECT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.THRIFT_MAGIC_FIELD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.THRIFT_MAGIC_FIELD_ID;

import java.util.Map;
import java.util.Set;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Wrapping client output protocol for injecting and propagating the trace header. This is also safe even if the server
 * doesn't deal with it.
 */
public class ClientOutProtocolWrapper extends TProtocolDecorator {

  private static final Logger log = LoggerFactory.getLogger(ClientOutProtocolWrapper.class);

  public ClientOutProtocolWrapper(TProtocol protocol) {
    super(protocol);
  }

  @Override
  public final void writeMessageBegin(TMessage message) throws TException {
    CLIENT_INJECT_THREAD.set(false);
    super.writeMessageBegin(message);
  }

  @Override
  public final void writeFieldStop() throws TException {
    boolean injected = CLIENT_INJECT_THREAD.get();
    if (!injected) {
      try {
        writeHeader(CONTEXT_THREAD.get());
      } catch (Throwable throwable) {
        if (log.isDebugEnabled()) {
          log.error("inject exception", throwable);
        }
      } finally {
        CLIENT_INJECT_THREAD.set(true);
      }
    }
    super.writeFieldStop();
  }

  private void writeHeader(Map<String, String> header) throws TException {
    super.writeFieldBegin(new TField(THRIFT_MAGIC_FIELD, TType.MAP, THRIFT_MAGIC_FIELD_ID));
    super.writeMapBegin(new TMap(TType.STRING, TType.STRING, header.size()));

    Set<Map.Entry<String, String>> entries = header.entrySet();
    for (Map.Entry<String, String> entry : entries) {
      super.writeString(entry.getKey());
      super.writeString(entry.getValue());
      if (log.isDebugEnabled()) {
        log.debug("client header >> " + entry.getKey() + "\t=\t" + entry.getValue());
      }
    }

    super.writeMapEnd();
    super.writeFieldEnd();
  }
}
