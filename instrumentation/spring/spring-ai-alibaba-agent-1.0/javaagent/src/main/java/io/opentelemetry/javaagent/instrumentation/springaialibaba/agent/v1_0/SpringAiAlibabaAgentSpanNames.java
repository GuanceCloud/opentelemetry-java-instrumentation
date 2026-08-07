/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

final class SpringAiAlibabaAgentSpanNames {
  static String agent(AgentRequest request) {
    return request.getOperation() + "_agent " + request.getAgentName();
  }

  static String tool(ToolRequest request) {
    return "execute_tool " + request.getToolName();
  }

  private SpringAiAlibabaAgentSpanNames() {}
}
