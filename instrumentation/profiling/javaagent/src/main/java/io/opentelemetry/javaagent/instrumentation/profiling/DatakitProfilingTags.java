/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.profiling;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

final class DatakitProfilingTags {

  private static final AttributeKey<String> serviceNameKey = stringKey("service.name");
  private static final AttributeKey<String> deploymentEnvironmentNameKey =
      stringKey("deployment.environment.name");
  private static final AttributeKey<String> deploymentEnvironmentKey =
      stringKey("deployment.environment");
  private static final AttributeKey<String> serviceVersionKey = stringKey("service.version");
  private static final AttributeKey<String> hostNameKey = stringKey("host.name");
  private static final AttributeKey<Long> processPidKey = longKey("process.pid");
  private static final AttributeKey<String> telemetryDistroNameKey =
      stringKey("telemetry.distro.name");
  private static final AttributeKey<String> telemetryDistroVersionKey =
      stringKey("telemetry.distro.version");

  private DatakitProfilingTags() {}

  static Map<String, String> create(Resource resource) {
    Map<String, String> tags = new LinkedHashMap<>();
    putIfPresent(tags, "service", resource.getAttribute(serviceNameKey));
    putIfPresent(
        tags,
        "env",
        firstNonEmpty(
            resource.getAttribute(deploymentEnvironmentNameKey),
            resource.getAttribute(deploymentEnvironmentKey)));
    putIfPresent(tags, "version", resource.getAttribute(serviceVersionKey));
    putIfPresent(tags, "host", resource.getAttribute(hostNameKey));
    putIfPresent(tags, "process_id", processId(resource));
    tags.put("language", "jvm");
    putIfPresent(
        tags,
        "library_type",
        firstNonEmpty(resource.getAttribute(telemetryDistroNameKey), "opentelemetry-javaagent"));
    putIfPresent(tags, "library_version", resource.getAttribute(telemetryDistroVersionKey));
    return tags;
  }

  private static void putIfPresent(Map<String, String> target, String key, @Nullable String value) {
    if (value != null && !value.isEmpty()) {
      target.put(key, value);
    }
  }

  @Nullable
  private static String processId(Resource resource) {
    Long pid = resource.getAttribute(processPidKey);
    if (pid != null) {
      return Long.toString(pid);
    }
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    int separator = runtimeName.indexOf('@');
    return separator > 0 ? runtimeName.substring(0, separator) : runtimeName;
  }

  @Nullable
  private static String firstNonEmpty(@Nullable String first, @Nullable String second) {
    if (first != null && !first.isEmpty()) {
      return first;
    }
    if (second != null && !second.isEmpty()) {
      return second;
    }
    return null;
  }
}
