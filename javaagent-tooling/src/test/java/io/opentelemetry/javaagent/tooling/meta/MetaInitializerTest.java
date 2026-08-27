/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MetaInitializerTest {

  @AfterEach
  void tearDown() {
    MetaTelemetry.resetForTest();
  }

  @Test
  void collectorFailureDoesNotEscapeAgentInitialization() {
    Instrumentation instrumentation = mock(Instrumentation.class);
    doThrow(new SecurityException("denied"))
        .when(instrumentation)
        .addTransformer(any(ClassFileTransformer.class));
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.meta.exporter", "meta");
    properties.put("otel.exporter.meta.endpoint", "http://127.0.0.1:8889/v1/meta");

    MetaInitializer.initialize(DefaultConfigProperties.createFromMap(properties), instrumentation);

    assertThat(MetaTelemetry.isDisabled()).isTrue();
  }
}
