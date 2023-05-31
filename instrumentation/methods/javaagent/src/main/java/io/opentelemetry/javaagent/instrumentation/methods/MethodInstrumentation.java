/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.methods.MethodSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.instrumenter.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodInstrumentation implements TypeInstrumentation {
  private final String className;
  private final Set<String> methodNames;

  public MethodInstrumentation(String className, Set<String> methodNames) {
    this.className = className;
    this.methodNames = methodNames;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(className);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named(className));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf(methodNames.toArray(new String[0])),
        MethodInstrumentation.class.getName() + "$MethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class MethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.AllArguments Object[] args,
        @Advice.Origin Method method,
        @Advice.Local("otelMethod") ClassAndMethod classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      classAndMethod = ClassAndMethod.create(declaringClass, methodName);
      if (!instrumenter().shouldStart(parentContext, classAndMethod)) {
        return;
      }

      context = instrumenter().start(parentContext, classAndMethod);
      scope = context.makeCurrent();
      Span span = Span.fromContext(context);
      if (!CommonConfig.get().isMethodAttributeEnabled()) {
        return;
      }
      if (args != null) {
        Parameter[] parameters = method.getParameters();
        StringBuffer m2 = new StringBuffer(method.getName());
        m2.append("(");
        int maxSize = 1024;
        if (parameters.length > 0) {
          for (int i = 0; i < parameters.length; i++) {
            if (args[i] == null) {
              span.setAttribute(parameters[i].getName(), "null");
            } else {
              String value = args[i].toString();
              span.setAttribute(parameters[i].getName(), args[i].toString()
                  .substring(0, value.length() >= maxSize ? maxSize : value.length()));
            }
            m2.append(parameters[i].getType().getTypeName()).append(" ")
                .append(parameters[i].getName());
            if (i < parameters.length - 1) {
              m2.append(",");
            }
          }
        }
        m2.append(")");
        span.setAttribute("method_name", m2.toString());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Origin Method method,
        @Advice.Local("otelMethod") ClassAndMethod classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      scope.close();

      returnValue =
          AsyncOperationEndSupport.create(instrumenter(), Void.class, method.getReturnType())
              .asyncEnd(context, classAndMethod, returnValue, throwable);
    }
  }
}
