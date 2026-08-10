/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springaialibaba.agent.v1_0;

public final class ToolRequest {
  private final String agentName;
  private final String toolName;
  private final String toolCallId;
  private final String arguments;
  private final boolean parallelExecution;

  public ToolRequest(String agentName, Object toolCall, boolean parallelExecution) {
    this.agentName = agentName;
    this.toolName = extractToolName(toolCall);
    this.toolCallId = extractString(toolCall, "id");
    this.arguments = extractString(toolCall, "arguments");
    this.parallelExecution = parallelExecution;
  }

  public String getAgentName() {
    return agentName;
  }

  public String getToolName() {
    return toolName;
  }

  public String getToolCallId() {
    return toolCallId;
  }

  public String getArguments() {
    return arguments;
  }

  public boolean isParallelExecution() {
    return parallelExecution;
  }

  private static String extractToolName(Object toolCall) {
    return extractString(toolCall, "name");
  }

  private static String extractString(Object toolCall, String methodName) {
    try {
      return (String) toolCall.getClass().getMethod(methodName).invoke(toolCall);
    } catch (ReflectiveOperationException e) {
      return "unknown";
    }
  }
}
