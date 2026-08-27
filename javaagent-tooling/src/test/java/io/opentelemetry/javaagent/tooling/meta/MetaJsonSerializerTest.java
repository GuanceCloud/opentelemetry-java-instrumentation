/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MetaJsonSerializerTest {

  @Test
  void serializesVersionedBatchEnvelope() throws Exception {
    MetaEvent event =
        new MetaEvent(
            1234, "app-started", singletonMap("enabled_capabilities", asList("app-started")));
    MetaExportRequest request =
        new MetaExportRequest(
            7, 1235, "runtime-id", singletonMap("service.name", "checkout"), singletonList(event));

    byte[] json = new MetaJsonSerializer().serialize(request);
    JsonNode root = new ObjectMapper().readTree(new String(json, UTF_8));

    assertThat(root.get("api_version").asText()).isEqualTo("v1");
    assertThat(root.get("runtime_id").asText()).isEqualTo("runtime-id");
    assertThat(root.get("seq_id").asLong()).isEqualTo(7);
    assertThat(root.get("tracer_time").asLong()).isEqualTo(1235);
    assertThat(root.has("application")).isFalse();
    assertThat(root.has("host")).isFalse();
    assertThat(root.get("resource").get("service.name").asText()).isEqualTo("checkout");
    assertThat(root.get("events")).hasSize(1);
    JsonNode eventJson = root.get("events").get(0);
    assertThat(eventJson.has("sequence_id")).isFalse();
    assertThat(eventJson.get("timestamp").asLong()).isEqualTo(1234);
    assertThat(eventJson.get("request_type").asText()).isEqualTo("app-started");
    assertThat(eventJson.get("payload").get("enabled_capabilities").get(0).asText())
        .isEqualTo("app-started");
  }
}
