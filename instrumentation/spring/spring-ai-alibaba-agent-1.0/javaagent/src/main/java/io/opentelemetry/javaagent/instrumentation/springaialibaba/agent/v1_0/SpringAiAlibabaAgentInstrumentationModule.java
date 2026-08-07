/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringAiAlibabaAgentInstrumentationModule extends InstrumentationModule {

  public SpringAiAlibabaAgentInstrumentationModule() {
    super("spring-ai-alibaba-agent", "spring-ai-alibaba-agent-1.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.alibaba.cloud.ai.graph.agent.Agent");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new AgentInstrumentation(), new AgentToolNodeInstrumentation());
  }
}
