package io.opentelemetry.javaagent.instrumentation.xxljob;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

import static io.opentelemetry.javaagent.instrumentation.xxljob.JobConstants.INSTRUMENTATION_NAME;
import static java.util.Arrays.asList;

@AutoService(InstrumentationModule.class)
public class JobInstrumentationModule extends InstrumentationModule{
  public JobInstrumentationModule() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new GlueJobInstrumentation(),
        new MethodJobInstrumentation(),
        new ScriptJobInstrumentation(),
        new SimpleJobInstrumentation());
  }
}
