/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asyncprofiler;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

final class DatakitProfilingTags {

  private static final String ASYNC_PROFILER_LIBRARY_TYPE = "async_profiler";
  private static final String ASYNC_PROFILER_LIBRARY_VERSION = "4.4";

  private static final AttributeKey<String> serviceNameKey = AttributeKey.stringKey("service.name");
  private static final AttributeKey<String> deploymentEnvironmentNameKey =
      AttributeKey.stringKey("deployment.environment.name");
  private static final AttributeKey<String> deploymentEnvironmentKey =
      AttributeKey.stringKey("deployment.environment");
  private static final AttributeKey<String> serviceVersionKey =
      AttributeKey.stringKey("service.version");
  private static final AttributeKey<String> hostNameKey = AttributeKey.stringKey("host.name");
  private static final AttributeKey<Long> processPidKey = AttributeKey.longKey("process.pid");
  private static final AttributeKey<String> telemetryDistroNameKey =
      AttributeKey.stringKey("telemetry.distro.name");
  private static final AttributeKey<String> telemetryDistroVersionKey =
      AttributeKey.stringKey("telemetry.distro.version");

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
    tags.put("library_type", ASYNC_PROFILER_LIBRARY_TYPE);
    tags.put("library_version", ASYNC_PROFILER_LIBRARY_VERSION);
    putIfPresent(tags, "otel_distro_name", resource.getAttribute(telemetryDistroNameKey));
    putIfPresent(tags, "otel_distro_version", resource.getAttribute(telemetryDistroVersionKey));
    return tags;
  }

  private static void putIfPresent(Map<String, String> tags, String key, String value) {
    if (value != null && !value.isEmpty()) {
      tags.put(key, value);
    }
  }

  private static String processId(Resource resource) {
    Long processId = resource.getAttribute(processPidKey);
    if (processId != null) {
      return Long.toString(processId.longValue());
    }
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    int at = runtimeName.indexOf('@');
    if (at > 0) {
      return runtimeName.substring(0, at);
    }
    return runtimeName;
  }

  private static String firstNonEmpty(String preferred, String fallback) {
    if (preferred != null && !preferred.isEmpty()) {
      return preferred;
    }
    if (fallback != null && !fallback.isEmpty()) {
      return fallback;
    }
    return null;
  }
}
