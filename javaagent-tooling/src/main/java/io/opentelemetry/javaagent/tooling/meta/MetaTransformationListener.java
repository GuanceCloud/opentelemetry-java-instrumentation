/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/** Correlates Meta integration matches with the final result of a Byte Buddy transformation. */
public class MetaTransformationListener extends AgentBuilder.Listener.Adapter {

  @Override
  public void onTransformation(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module,
      boolean loaded,
      DynamicType dynamicType) {
    MetaTelemetry.integrationTransformationSucceeded(typeDescription.getName());
  }

  @Override
  public void onError(
      String typeName,
      ClassLoader classLoader,
      JavaModule module,
      boolean loaded,
      Throwable throwable) {
    MetaTelemetry.integrationTransformationFinished(typeName);
  }

  @Override
  public void onComplete(
      String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
    MetaTelemetry.integrationTransformationFinished(typeName);
  }
}
