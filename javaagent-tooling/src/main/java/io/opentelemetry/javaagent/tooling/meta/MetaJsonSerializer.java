/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class MetaJsonSerializer {

  private final ObjectMapper objectMapper;

  MetaJsonSerializer() {
    this(new ObjectMapper());
  }

  MetaJsonSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  byte[] serialize(MetaExportRequest request) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    serialize(request, output);
    return output.toByteArray();
  }

  void serialize(MetaExportRequest request, OutputStream output) throws IOException {
    try (JsonGenerator generator = objectMapper.getFactory().createGenerator(output)) {
      generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
      generator.writeStartObject();
      generator.writeStringField("api_version", request.getApiVersion());
      generator.writeStringField("runtime_id", request.getRuntimeId());
      generator.writeNumberField("seq_id", request.getSequenceId());
      generator.writeNumberField("tracer_time", request.getTracerTime());
      generator.writeObjectField("resource", request.getResource());
      generator.writeArrayFieldStart("events");
      for (MetaEvent event : request.getEvents()) {
        generator.writeStartObject();
        generator.writeNumberField("timestamp", event.getTimestamp());
        generator.writeStringField("request_type", event.getRequestType());
        generator.writeObjectField("payload", event.getPayload());
        generator.writeEndObject();
      }
      generator.writeEndArray();
      generator.writeEndObject();
    }
  }
}
