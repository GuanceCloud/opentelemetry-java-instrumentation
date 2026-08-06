/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.instrumentation.asyncprofiler.AsyncProfilerConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentAsyncProfilerConfigurationTest {

  @Test
  void shouldEnableLockAndMemoryProfilingByDefault() {
    ConfigProperties configProperties =
        DefaultConfigProperties.createFromMap(Map.of("otel.profiling.enabled", "true"));

    AsyncProfilerConfig config =
        AgentAsyncProfilerConfiguration.create(configProperties, Resource.empty());

    String command = config.toStartCommand("/tmp/profile.jfr");

    assertTrue(command.contains("event=cpu"));
    assertTrue(command.contains("alloc=1m"));
    assertTrue(command.contains("lock=10ms"));
  }
}
