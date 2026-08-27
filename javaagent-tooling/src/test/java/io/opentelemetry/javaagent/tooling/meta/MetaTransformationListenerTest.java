/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetaTransformationListenerTest {

  @BeforeEach
  @AfterEach
  void resetMetaTelemetry() {
    MetaTelemetry.resetForTest();
  }

  @Test
  void exportsIntegrationOnlyAfterByteBuddyReportsSuccessfulTransformation() throws Exception {
    Instrumentation instrumentation = ByteBuddyAgent.install();
    MetaTelemetry.initialize(integrationOnlyConfiguration(), instrumentation);
    QueueingExporter exporter = new QueueingExporter();
    MetaService service =
        new MetaService(exporter, 10, 1_000, "runtime-id", singletonMap("service.name", "test"));
    service.start();
    MetaTelemetry.start(service);

    String className =
        "io.opentelemetry.javaagent.tooling.meta.Generated"
            + UUID.randomUUID().toString().replace("-", "");
    ClassFileTransformer transformer =
        new AgentBuilder.Default()
            .with(new MetaTransformationListener())
            .type(named(className))
            .transform(
                (builder, typeDescription, classLoader, module, protectionDomain) -> {
                  MetaTelemetry.integrationMatched(typeDescription.getName(), "test-integration");
                  return builder.defineField("instrumented", boolean.class, Visibility.PUBLIC);
                })
            .installOn(instrumentation);
    try {
      Class<?> transformed =
          new ByteBuddy()
              .subclass(Object.class)
              .name(className)
              .make()
              .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
              .getLoaded();

      assertThat(transformed.getField("instrumented")).isNotNull();
      MetaExportRequest request = exporter.requests.poll(5, SECONDS);
      assertThat(request).isNotNull();
      assertThat(request.getEvents()).hasSize(1);
      MetaEvent event = request.getEvents().get(0);
      assertThat(event.getRequestType()).isEqualTo("app-integrations-change");
      Map<String, Object> integration = new LinkedHashMap<>();
      integration.put("name", "test-integration");
      integration.put("enabled", true);
      assertThat(event.getPayload().get("integrations")).isEqualTo(singletonList(integration));
    } finally {
      instrumentation.removeTransformer(transformer);
      MetaTelemetry.shutdown(service);
    }
  }

  private static MetaConfiguration integrationOnlyConfiguration() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.meta.exporter", "meta");
    properties.put("otel.exporter.meta.endpoint", "http://127.0.0.1:8889/v1/meta");
    properties.put("otel.meta.app-started.enabled", "false");
    properties.put("otel.meta.app-dependencies-loaded.enabled", "false");
    properties.put("otel.meta.app-integrations-change.enabled", "true");
    return MetaConfiguration.create(DefaultConfigProperties.createFromMap(properties));
  }

  private static final class QueueingExporter implements MetaExporter {
    private final BlockingQueue<MetaExportRequest> requests = new LinkedBlockingQueue<>();

    @Override
    public MetaExportResult export(MetaExportRequest request) {
      requests.add(request);
      return MetaExportResult.SUCCESS;
    }
  }
}
