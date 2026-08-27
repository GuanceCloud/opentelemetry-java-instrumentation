/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetaCollectorsTest {

  @Test
  void extendedHeartbeatHasStableFullSnapshotShape() {
    assertThat(MetaTelemetry.extendedHeartbeatPayload())
        .containsEntry("configuration", emptyList())
        .containsEntry("dependencies", emptyList())
        .containsEntry("integrations", emptyList());
  }

  @Test
  void exportsAppliedIntegrationsRecordedBeforeServiceStarts() throws Exception {
    QueueingExporter exporter = new QueueingExporter();
    MetaService service = newService(exporter);
    MetaIntegrationCollector collector = new MetaIntegrationCollector();
    try {
      collector.record(asList("spring-webmvc", "spring-webmvc", "jdbc"));
      service.start();
      collector.start(service);

      MetaEvent event = nextEvent(exporter);
      assertThat(event.getRequestType()).isEqualTo("app-integrations-change");
      Map<String, Object> spring = new LinkedHashMap<>();
      spring.put("name", "spring-webmvc");
      spring.put("enabled", true);
      Map<String, Object> jdbc = new LinkedHashMap<>();
      jdbc.put("name", "jdbc");
      jdbc.put("enabled", true);
      assertThat(event.getPayload().get("integrations")).isEqualTo(asList(jdbc, spring));

      collector.record(singletonList("servlet"));

      Map<String, Object> servlet = new LinkedHashMap<>();
      servlet.put("name", "servlet");
      servlet.put("enabled", true);
      MetaEvent updated = nextEvent(exporter);
      assertThat(updated.getPayload().get("integrations")).isEqualTo(asList(jdbc, servlet, spring));
    } finally {
      collector.close();
      service.close();
    }
  }

  @Test
  void exportsEachDependencyLocationOnlyOnce(@TempDir Path tempDir) throws Exception {
    Path jar = tempDir.resolve("demo.jar");
    writeMavenJar(jar, "demo");
    Path loggingJar = tempDir.resolve("logging.jar");
    writeMavenJar(loggingJar, "logging");
    Path agentJar = tempDir.resolve("javaagent.jar");
    writeMavenJar(agentJar, "javaagent");
    QueueingExporter exporter = new QueueingExporter();
    MetaService service = newService(exporter);
    MetaDependencyCollector collector = new MetaDependencyCollector(agentJar.toUri().toURL());
    try {
      collector.recordLocation(agentJar.toUri().toURL());
      collector.recordLocation(jar.toUri().toURL());
      collector.recordLocation(jar.toUri().toURL());
      service.start();
      collector.start(service);

      MetaEvent event = nextEvent(exporter);
      assertThat(event.getRequestType()).isEqualTo("app-dependencies-loaded");
      Map<String, Object> dependency = new LinkedHashMap<>();
      dependency.put("name", "com.example:demo");
      dependency.put("version", "1.0.0");
      assertThat(event.getPayload().get("dependencies")).isEqualTo(singletonList(dependency));

      collector.recordLocation(loggingJar.toUri().toURL());

      Map<String, Object> logging = new LinkedHashMap<>();
      logging.put("name", "com.example:logging");
      logging.put("version", "1.0.0");
      MetaEvent updated = nextEvent(exporter);
      assertThat(updated.getPayload().get("dependencies")).isEqualTo(asList(dependency, logging));
    } finally {
      collector.close();
      service.close();
    }
  }

  @Test
  void retainsNoMoreThanConfiguredDependencyLimit(@TempDir Path tempDir) throws Exception {
    Path firstJar = tempDir.resolve("first.jar");
    writeMavenJar(firstJar, "first");
    Path secondJar = tempDir.resolve("second.jar");
    writeMavenJar(secondJar, "second");
    QueueingExporter exporter = new QueueingExporter();
    MetaService service = newService(exporter);
    MetaDependencyCollector collector = new MetaDependencyCollector(null, 2, 1);
    try {
      collector.recordLocation(firstJar.toUri().toURL());
      collector.recordLocation(secondJar.toUri().toURL());
      service.start();
      collector.start(service);

      MetaEvent event = nextEvent(exporter);
      Map<String, Object> expectedDependency = new LinkedHashMap<>();
      expectedDependency.put("name", "com.example:first");
      expectedDependency.put("version", "1.0.0");
      assertThat(event.getPayload().get("dependencies"))
          .isEqualTo(singletonList(expectedDependency));
      assertThat(collector.snapshot()).hasSize(1);
    } finally {
      collector.close();
      service.close();
    }
  }

  private static MetaService newService(MetaExporter exporter) {
    return new MetaService(exporter, 10, 1_000, "runtime-id", singletonMap("service.name", "test"));
  }

  private static MetaEvent nextEvent(QueueingExporter exporter) throws InterruptedException {
    MetaExportRequest request = exporter.requests.poll(5, SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getEvents()).hasSize(1);
    return request.getEvents().get(0);
  }

  private static void writeMavenJar(Path path, String artifactId) throws IOException {
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("META-INF/maven/com.example/demo/pom.properties"));
      output.write(
          ("groupId=com.example\nartifactId=" + artifactId + "\nversion=1.0.0\n")
              .getBytes(ISO_8859_1));
      output.closeEntry();
    }
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
