/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

public final class AgentRequest {
  private final String agentName;
  private final String operation;

  public AgentRequest(String agentName, String operation) {
    this.agentName = agentName;
    this.operation = operation;
  }

  public String getAgentName() {
    return agentName;
  }

  public String getOperation() {
    return operation;
  }
}
