/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetaResourceTest {

  @Test
  void combinesRuntimeAndHostMetadataWithoutDdAliases() {
    Resource source =
        Resource.create(
            Attributes.builder()
                .put(stringKey("service.name"), "checkout")
                .put(stringKey("service.version"), "1.2.3")
                .put(stringKey("deployment.environment.name"), "production")
                .put(stringKey("host.name"), "checkout-host")
                .put(stringKey("os.description"), "resource-os-description")
                .put(stringKey("os.kernel.version"), "resource-kernel-version")
                .build());

    Map<String, Object> resource = MetaResource.from(source, "2.30.2");

    assertThat(resource)
        .containsEntry("service.name", "checkout")
        .containsEntry("service.version", "1.2.3")
        .containsEntry("deployment.environment.name", "production")
        .containsEntry("telemetry.distro.version", "2.30.2")
        .containsEntry("telemetry.sdk.language", "java")
        .containsEntry("host.name", "checkout-host")
        .containsEntry("os.description", "resource-os-description")
        .containsEntry("os.kernel.version", "resource-kernel-version")
        .doesNotContainKeys(
            "service_name", "service_version", "env", "tracer_version", "hostname", "architecture");
    assertThat(resource.get("host.arch").toString()).isNotEmpty();
    assertThat(resource.get("process.runtime.name").toString()).isNotEmpty();
    assertThat(resource.get("process.runtime.version").toString()).isNotEmpty();
    assertThat(resource.get("os.type").toString()).isNotEmpty();
    assertThat(resource.get("os.name").toString()).isNotEmpty();
  }
}
