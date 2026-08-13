/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeMessages;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeResponses;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiMessageAttributes.serializeSystemInstructions;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.springai.v1_0.app.TestChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiMessageAttributesTest {

  @Test
  void serializesSystemInstructionsSeparatelyFromInputMessages() {
    SpringAiMessageAttributes.SerializedMessages systemInstructions =
        serializeSystemInstructions(
            asList(new SystemMessage("line\nvalue"), new UserMessage("123456789😀")), 10);
    SpringAiMessageAttributes.SerializedMessages messages =
        serializeMessages(
            asList(new SystemMessage("line\nvalue"), new UserMessage("123456789😀")), 10);

    assertThat(systemInstructions.json())
        .isEqualTo("[{\"type\":\"text\",\"content\":\"line\\nvalue\"}]");
    assertThat(systemInstructions.truncated()).isFalse();
    assertThat(messages.json())
        .isEqualTo("[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"123456789\"}]}]");
    assertThat(messages.truncated()).isTrue();
  }

  @Test
  void serializesAndTruncatesOutputMessageContent() {
    ChatResponse response = new TestChatModel().call(new Prompt("ignored"));
    SpringAiMessageAttributes.SerializedMessages messages =
        serializeResponses(response, "first\n\"quoted\"", 11);

    assertThat(messages.json())
        .isEqualTo(
            "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"first\\n\\\"quot\"}],\"finish_reason\":\"stop\"}]");
    assertThat(messages.truncated()).isTrue();
  }
}
