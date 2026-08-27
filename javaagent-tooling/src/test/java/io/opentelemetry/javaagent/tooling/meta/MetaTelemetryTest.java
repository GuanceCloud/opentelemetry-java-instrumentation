/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetaTelemetryTest {

  @Test
  void keepsNestedTransformationsIsolatedByTypeName() {
    MetaTelemetry.PendingIntegrations pending = new MetaTelemetry.PendingIntegrations();

    pending.add("example.Outer", "outer-instrumentation");
    pending.add("example.Inner", "inner-instrumentation");

    assertThat(pending.remove("example.Inner")).containsExactly("inner-instrumentation");
    assertThat(pending.remove("example.Unrelated")).isNull();
    assertThat(pending.remove("example.Outer")).containsExactly("outer-instrumentation");
  }

  @Test
  void combinesMultipleInstrumentationsForTheSameType() {
    MetaTelemetry.PendingIntegrations pending = new MetaTelemetry.PendingIntegrations();

    pending.add("example.Application", "servlet");
    pending.add("example.Application", "spring-webmvc");
    pending.add("example.Application", "servlet");

    assertThat(pending.remove("example.Application")).containsExactly("servlet", "spring-webmvc");
    assertThat(pending.remove("example.Application")).isNull();
  }
}
