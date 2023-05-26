package io.opentelemetry.instrumentation.apachethrift;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.opentelemetry.instrumentation.apachethrift.ThriftConstants.CONTEXT_THREAD;
import static io.opentelemetry.instrumentation.apachethrift.ThriftSingletons.serverInstrumenter;


@SuppressWarnings({"unchecked","unused"})
public class ServerInProtocolWrapper extends TProtocolDecorator {
  public static final Logger log = LoggerFactory.getLogger(ServerInProtocolWrapper.class);

  public ServerInProtocolWrapper(TProtocol protocol) {
    super(protocol);
  }

  private Context parentContext;
  public void initial(AbstractContext context,Context parentContext) {
    CONTEXT_THREAD.set(context);
    this.parentContext = parentContext;
  }

  @Override
  public TField readFieldBegin() throws TException {
    TField field = super.readFieldBegin();
    if (field.id == ThriftConstants.THRIFT_MAGIC_FIELD_ID && field.type == TType.MAP) {
      try {
        TMap tMap = super.readMapBegin();
        Map<String, String> header = new HashMap<>(tMap.size);

        for (int i = 0; i < tMap.size; i++) {
          String key = readString();
          String value = readString();
          header.put(key, value);
          if (log.isDebugEnabled()) {
            log.debug("receive header >> " + key + "\t=\t" + value);
          }
        }

        AbstractContext context = CONTEXT_THREAD.get();
        context.setCreatedSpan(true);
        context.putAll(header);
        if (parentContext==null){
          parentContext = Context.current();
        }
        if (serverInstrumenter().shouldStart(parentContext, context)) {
          Context otelContext = serverInstrumenter().start(parentContext, context);
          Scope scope = otelContext.makeCurrent();
          Span span = Span.fromContext(otelContext);
          CONTEXT_THREAD.set(context);
        }
      } catch (Throwable throwable) {
        log.error("readFieldBegin exception", throwable);
        throw throwable;
      } finally {
        super.readMapEnd();
        super.readFieldEnd();
//        readFieldEnd();
      }
      return readFieldBegin();
    }

    return field;
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    TMessage message = super.readMessageBegin();
    if (Objects.nonNull(message)) {
      AbstractContext context = CONTEXT_THREAD.get();
      if (context == null) {
        context = new ThriftContext(null);
        CONTEXT_THREAD.set(context);
      }
      context.setup(message.name);
    }
    return message;
  }

}
