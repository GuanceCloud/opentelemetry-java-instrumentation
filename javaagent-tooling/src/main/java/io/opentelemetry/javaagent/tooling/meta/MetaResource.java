/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class MetaResource {

  @SuppressWarnings("InlineTrivialConstant") // Works around Error Prone DeduplicateConstants crash.
  private static final String EMPTY = "";

  private static final Path OS_RELEASE = Paths.get("/etc/os-release");
  private static final Path PROC_VERSION = Paths.get("/proc/version");

  private static final Set<String> ALLOWED_ATTRIBUTES =
      new HashSet<>(
          asList(
              "service.name",
              "service.namespace",
              "service.version",
              "service.instance.id",
              "deployment.environment.name",
              "telemetry.sdk.name",
              "telemetry.sdk.language",
              "telemetry.sdk.version",
              "telemetry.distro.name",
              "telemetry.distro.version",
              "process.pid",
              "process.executable.name",
              "process.runtime.name",
              "process.runtime.version",
              "process.runtime.description",
              "os.type",
              "os.name",
              "os.description",
              "os.version",
              "os.kernel.version",
              "host.name",
              "host.arch",
              "host.id",
              "container.id",
              "container.name",
              "k8s.cluster.name",
              "k8s.namespace.name",
              "k8s.node.name",
              "k8s.pod.name",
              "k8s.pod.uid"));

  static Map<String, Object> from(Resource resource, @Nullable String agentVersion) {
    Map<String, Object> result = new LinkedHashMap<>();
    resource
        .getAttributes()
        .forEach(
            (key, value) -> {
              if (ALLOWED_ATTRIBUTES.contains(key.getKey())) {
                result.put(key.getKey(), value);
              }
            });

    putIfMissing(result, "telemetry.distro.version", agentVersion);
    putIfMissing(result, "telemetry.sdk.language", "java");
    putIfMissing(result, "process.runtime.name", property("java.runtime.name"));
    putIfMissing(result, "process.runtime.version", property("java.runtime.version"));
    putIfMissing(result, "process.runtime.description", runtimeDescription());
    if (!result.containsKey("host.name")) {
      putIfMissing(result, "host.name", hostname());
    }
    putIfMissing(result, "host.arch", normalizeArchitecture(property("os.arch")));
    String osName = property("os.name");
    putIfMissing(result, "os.type", normalizeOsType(osName));
    putIfMissing(result, "os.name", osName);
    putIfMissing(result, "os.version", property("os.version"));

    if (shouldUseOsReleaseDescription(result)) {
      String osDescription = osReleaseDescription();
      if (!osDescription.isEmpty()) {
        // The Linux distribution description is more useful than the JVM's kernel-only default.
        result.put("os.description", osDescription);
      }
    }
    if (!result.containsKey("os.kernel.version")) {
      putIfMissing(result, "os.kernel.version", kernelVersion());
    }
    return unmodifiableMap(result);
  }

  private static boolean shouldUseOsReleaseDescription(Map<String, Object> resource) {
    Object current = resource.get("os.description");
    if (current == null) {
      return true;
    }
    Object osName = resource.get("os.name");
    Object osVersion = resource.get("os.version");
    return osName != null
        && osVersion != null
        && current.toString().equals(osName + " " + osVersion);
  }

  private static void putIfMissing(
      Map<String, Object> resource, String key, @Nullable String value) {
    if (!resource.containsKey(key) && value != null && !value.isEmpty()) {
      resource.put(key, value);
    }
  }

  private static String hostname() {
    try {
      return emptyIfNull(InetAddress.getLocalHost().getHostName());
    } catch (IOException | SecurityException ignored) {
      return EMPTY;
    }
  }

  private static String runtimeDescription() {
    String vendor = property("java.vm.vendor");
    String name = property("java.vm.name");
    String version = property("java.vm.version");
    return joinNonEmpty(vendor, name, version);
  }

  private static String normalizeArchitecture(String architecture) {
    switch (architecture.toLowerCase(Locale.ROOT)) {
      case "x86_64":
      case "amd64":
        return "amd64";
      case "x86":
      case "i386":
      case "i486":
      case "i586":
      case "i686":
        return "x86";
      case "aarch64":
      case "arm64":
        return "arm64";
      case "arm":
      case "arm32":
        return "arm32";
      case "ppc":
      case "ppc32":
        return "ppc32";
      case "ppc64":
      case "ppc64le":
        return "ppc64";
      default:
        return architecture;
    }
  }

  private static String normalizeOsType(String osName) {
    String normalized = osName.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("windows")) {
      return "windows";
    }
    if (normalized.startsWith("mac") || normalized.startsWith("darwin")) {
      return "darwin";
    }
    if (normalized.startsWith("linux")) {
      return "linux";
    }
    if (normalized.startsWith("sunos")) {
      return "solaris";
    }
    if (normalized.startsWith("hp-ux")) {
      return "hpux";
    }
    return normalized.replace('-', '_').replace(' ', '_');
  }

  private static String osReleaseDescription() {
    try {
      if (!Files.isRegularFile(OS_RELEASE)) {
        return EMPTY;
      }
      String name = EMPTY;
      String version = EMPTY;
      for (String line : Files.readAllLines(OS_RELEASE, ISO_8859_1)) {
        int separator = line.indexOf('=');
        if (separator <= 0) {
          continue;
        }
        String key = line.substring(0, separator);
        String value = unquote(line.substring(separator + 1));
        if ("PRETTY_NAME".equals(key) && !value.isEmpty()) {
          return value;
        }
        if ("NAME".equals(key)) {
          name = value;
        } else if ("VERSION".equals(key)) {
          version = value;
        }
      }
      return joinNonEmpty(name, version);
    } catch (IOException | SecurityException ignored) {
      return EMPTY;
    }
  }

  private static String kernelVersion() {
    try {
      if (!Files.isRegularFile(PROC_VERSION)) {
        return EMPTY;
      }
      List<String> lines = Files.readAllLines(PROC_VERSION, ISO_8859_1);
      if (lines.isEmpty()) {
        return EMPTY;
      }
      String version = lines.get(0).trim();
      int buildMarker = version.indexOf('#');
      return buildMarker < 0 ? version : version.substring(buildMarker);
    } catch (IOException | SecurityException ignored) {
      return EMPTY;
    }
  }

  private static String property(String name) {
    try {
      return emptyIfNull(System.getProperty(name));
    } catch (SecurityException ignored) {
      return EMPTY;
    }
  }

  private static String joinNonEmpty(String... values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (value.isEmpty()) {
        continue;
      }
      if (result.length() > 0) {
        result.append(' ');
      }
      result.append(value);
    }
    return result.toString();
  }

  private static String unquote(String value) {
    String trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.charAt(0) == '"' && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String emptyIfNull(@Nullable String value) {
    return value == null ? EMPTY : value;
  }

  private MetaResource() {}
}
