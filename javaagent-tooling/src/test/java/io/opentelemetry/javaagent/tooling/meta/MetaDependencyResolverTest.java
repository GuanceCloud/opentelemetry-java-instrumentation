/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetaDependencyResolverTest {

  @Test
  void resolvesMavenCoordinatesFromPomProperties(@TempDir Path tempDir) throws Exception {
    Path jar = tempDir.resolve("renamed.jar");
    writeJar(
        jar,
        null,
        "META-INF/maven/com.example/demo/pom.properties",
        "groupId=com.example\nartifactId=demo\nversion=1.2.3\n".getBytes(ISO_8859_1));

    List<MetaDependency> dependencies = MetaDependencyResolver.resolve(jar.toUri().toURL());

    assertThat(dependencies).hasSize(1);
    assertThat(dependencies.get(0).toPayload())
        .containsExactlyInAnyOrderEntriesOf(dependencyPayload("com.example:demo", "1.2.3"));
  }

  @Test
  void fallsBackToManifestFileNameAndHash(@TempDir Path tempDir) throws Exception {
    Path jar = tempDir.resolve("fallback-lib-4.5.6.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Implementation-Title", "fallback-lib");
    writeJar(jar, manifest, "example.txt", new byte[] {1, 2, 3});

    List<MetaDependency> dependencies = MetaDependencyResolver.resolve(jar.toUri().toURL());

    assertThat(dependencies).hasSize(1);
    assertThat(dependencies.get(0).toPayload())
        .containsEntry("name", "fallback-lib")
        .containsEntry("version", "4.5.6");
    assertThat(dependencies.get(0).toPayload().get("hash").toString()).matches("[0-9A-F]{40}");
  }

  @Test
  void ignoresAutomaticModuleNameWhenGuessingFallbackName(@TempDir Path tempDir) throws Exception {
    Path jar = tempDir.resolve("spring-core-6.1.1.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Automatic-Module-Name", "spring.core");
    manifest.getMainAttributes().putValue("Implementation-Title", "Spring Core");
    writeJar(jar, manifest, "example.txt", new byte[] {1});

    List<MetaDependency> dependencies = MetaDependencyResolver.resolve(jar.toUri().toURL());

    assertThat(dependencies).hasSize(1);
    assertThat(dependencies.get(0).toPayload())
        .containsEntry("name", "spring-core")
        .containsEntry("version", "6.1.1");
  }

  @Test
  void resolvesDependencyFromNestedJar(@TempDir Path tempDir) throws Exception {
    byte[] nestedJar =
        jarBytes(
            null,
            "META-INF/maven/org.example/nested/pom.properties",
            "groupId=org.example\nartifactId=nested\nversion=7.8.9\n".getBytes(ISO_8859_1));
    Path outerJar = tempDir.resolve("application.jar");
    writeJar(outerJar, null, "BOOT-INF/lib/nested-7.8.9.jar", nestedJar);

    List<MetaDependency> dependencies =
        MetaDependencyResolver.resolve(
            new URL("jar:" + outerJar.toUri() + "!/BOOT-INF/lib/nested-7.8.9.jar!/"));

    assertThat(dependencies).hasSize(1);
    assertThat(dependencies.get(0).toPayload())
        .containsExactlyInAnyOrderEntriesOf(dependencyPayload("org.example:nested", "7.8.9"));
  }

  @Test
  void resolvesMavenCoordinatesFromExplodedDirectory(@TempDir Path tempDir) throws Exception {
    Path classes = tempDir.resolve("classes");
    Path properties = classes.resolve("META-INF/maven/com.example/exploded/pom.properties");
    Files.createDirectories(properties.getParent());
    Files.write(
        properties,
        "groupId=com.example\nartifactId=exploded\nversion=1.0.0\n".getBytes(ISO_8859_1));

    List<MetaDependency> dependencies = MetaDependencyResolver.resolve(classes.toUri().toURL());

    assertThat(dependencies).hasSize(1);
    assertThat(dependencies.get(0).toPayload())
        .containsExactlyInAnyOrderEntriesOf(dependencyPayload("com.example:exploded", "1.0.0"));
  }

  @Test
  void rejectsOversizedPomProperties(@TempDir Path tempDir) throws Exception {
    Path jar = tempDir.resolve("oversized.jar");
    byte[] content = new byte[65 * 1024];
    writeJar(jar, null, "META-INF/maven/com.example/demo/pom.properties", content);

    assertThat(MetaDependencyResolver.resolve(jar.toUri().toURL())).isEmpty();
  }

  private static Map<String, Object> dependencyPayload(String name, String version) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    payload.put("version", version);
    return payload;
  }

  private static void writeJar(Path path, Manifest manifest, String entryName, byte[] content)
      throws IOException {
    try (JarOutputStream output =
        manifest == null
            ? new JarOutputStream(Files.newOutputStream(path))
            : new JarOutputStream(Files.newOutputStream(path), manifest)) {
      output.putNextEntry(new JarEntry(entryName));
      output.write(content);
      output.closeEntry();
    }
  }

  private static byte[] jarBytes(Manifest manifest, String entryName, byte[] content)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (JarOutputStream output =
        manifest == null ? new JarOutputStream(bytes) : new JarOutputStream(bytes, manifest)) {
      output.putNextEntry(new JarEntry(entryName));
      output.write(content);
      output.closeEntry();
    }
    return bytes.toByteArray();
  }
}
