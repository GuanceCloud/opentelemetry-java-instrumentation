/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachethrift;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

import static java.util.Arrays.asList;

@AutoService(InstrumentationModule.class)
public class ThriftInstrumentationModule extends InstrumentationModule {
  public ThriftInstrumentationModule() {
    super("alibaba-thrift", "thrift");
  }


  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new TClientInstrumentation(),
        new TServerInstrumentation(),
        new TAsyncMethodCallInstrumentation(),
        new TAsyncClientInstrumentation(),
        new TBaseProcessorInstrumentation(),
        new TBaseAsyncProcessorInstrumentation(),
        new TMultiplexedProcessorInstrumentation(),
        new TProcessorInstrumentation()
    );
  }
}
