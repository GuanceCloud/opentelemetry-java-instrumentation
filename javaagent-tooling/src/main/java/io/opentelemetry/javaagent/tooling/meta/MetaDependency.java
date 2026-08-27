/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

final class MetaDependency {

  private final String name;
  private final String version;
  @Nullable private final String hash;

  MetaDependency(String name, String version, @Nullable String hash) {
    this.name = name;
    this.version = version;
    this.hash = hash;
  }

  String key() {
    return name + '\u0000' + version + '\u0000' + (hash == null ? "" : hash);
  }

  Map<String, Object> toPayload() {
    Map<String, Object> dependency = new LinkedHashMap<>();
    dependency.put("name", name);
    if (!version.isEmpty()) {
      dependency.put("version", version);
    }
    if (hash != null) {
      dependency.put("hash", hash);
    }
    return dependency;
  }
}
