package com.lifeagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeagent.dto.turn.ContextMessageItem;
import com.lifeagent.dto.turn.ContextPackage;
import com.lifeagent.dto.turn.TurnResolutionRequest;
import com.lifeagent.dto.turn.TurnResolutionResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TurnResolutionProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldUseSameSnakeCaseProtocolAsPython() throws Exception {
        TurnResolutionRequest request = TurnResolutionRequest.builder()
                .requestId("request-1")
                .schemaVersion("1")
                .context(ContextPackage.builder()
                        .currentMessage("你好")
                        .memorySummary(null)
                        .recentMessages(List.of())
                        .summaryRequested(false)
                        .summaryMessages(List.of())
                        .build())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("request_id").asText()).isEqualTo("request-1");
        assertThat(json.get("schema_version").asText()).isEqualTo("1");
        assertThat(json.get("context").get("current_message").asText()).isEqualTo("你好");
        assertThat(json.get("context").get("summary_requested").asBoolean()).isFalse();
    }

    @Test
    void shouldDeserializeValidatedPythonResponse() throws Exception {
        String json = """
                {
                  "request_id": "request-1",
                  "schema_version": "1",
                  "resolution_status": "RESOLVED",
                  "intent": "SMALL_TALK",
                  "confidence": 0.95,
                  "reply_draft": "你好，我已经准备好了。",
                  "updated_summary": null
                }
                """;

        TurnResolutionResponse response = objectMapper.readValue(json, TurnResolutionResponse.class);

        assertThat(response.getResolutionStatus()).isEqualTo("RESOLVED");
        assertThat(response.getIntent()).isEqualTo("SMALL_TALK");
        assertThat(response.getConfidence()).isEqualTo(0.95);
    }

    @Test
    void shouldDeserializeConfidenceOutsideJavaRange() throws Exception {
        // Java 侧不验证 confidence 范围，由 Python Pydantic 保证
        String json = """
                {
                  "request_id": "request-1",
                  "schema_version": "1",
                  "resolution_status": "RESOLVED",
                  "intent": "SMALL_TALK",
                  "confidence": 1.5,
                  "reply_draft": "hello",
                  "updated_summary": null
                }
                """;

        TurnResolutionResponse response = objectMapper.readValue(json, TurnResolutionResponse.class);
        assertThat(response.getConfidence()).isEqualTo(1.5);
    }

    @Test
    void shouldAcceptPythonCrossFieldValidatedResponse() throws Exception {
        // 跨字段约束由 Python Pydantic 验证，Java 信任 Python 返回
        String resolvedWithPlan = """
                {
                  "request_id": "request-1",
                  "schema_version": "1",
                  "resolution_status": "RESOLVED",
                  "intent": "PLAN_CREATE",
                  "confidence": 0.9,
                  "reply_draft": "reply",
                  "updated_summary": null
                }
                """;

        TurnResolutionResponse response = objectMapper.readValue(resolvedWithPlan, TurnResolutionResponse.class);
        assertThat(response.getResolutionStatus()).isEqualTo("RESOLVED");
        assertThat(response.getIntent()).isEqualTo("PLAN_CREATE");
    }

    @Test
    void shouldRejectSchemaVersionNotNumeric() {
        Set<?> violations = validator.validate(
                TurnResolutionRequest.builder()
                        .requestId("r1")
                        .schemaVersion("v1")
                        .context(ContextPackage.builder()
                                .currentMessage("hi")
                                .memorySummary(null)
                                .recentMessages(List.of())
                                .summaryRequested(false)
                                .summaryMessages(List.of())
                                .build())
                        .build());
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldAcceptSchemaVersionNumeric() {
        Set<?> violations = validator.validate(
                TurnResolutionRequest.builder()
                        .requestId("r1")
                        .schemaVersion("1")
                        .context(ContextPackage.builder()
                                .currentMessage("hi")
                                .memorySummary(null)
                                .recentMessages(List.of())
                                .summaryRequested(false)
                                .summaryMessages(List.of())
                                .build())
                        .build());
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRoundTripContextMessageItem() throws Exception {
        ContextMessageItem item = ContextMessageItem.builder()
                .messageId(42L)
                .role("USER")
                .content("你好")
                .build();

        String json = objectMapper.writeValueAsString(item);
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("message_id").asLong()).isEqualTo(42);
        assertThat(node.get("role").asText()).isEqualTo("USER");
        assertThat(node.get("content").asText()).isEqualTo("你好");

        ContextMessageItem deserialized = objectMapper.readValue(json, ContextMessageItem.class);
        assertThat(deserialized.getMessageId()).isEqualTo(42);
        assertThat(deserialized.getRole()).isEqualTo("USER");
        assertThat(deserialized.getContent()).isEqualTo("你好");
    }
}
