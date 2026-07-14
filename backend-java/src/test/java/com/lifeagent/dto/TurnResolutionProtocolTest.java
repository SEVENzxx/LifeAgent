package com.lifeagent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeagent.enums.RelationType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurnResolutionProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldUseSameSnakeCaseProtocolAsPython() throws Exception {
        TurnResolutionRequest request = new TurnResolutionRequest(
                "request-1",
                "1",
                new ContextPackage("你好", List.of("flow-1"))
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("request_id").asText()).isEqualTo("request-1");
        assertThat(json.get("schema_version").asText()).isEqualTo("1");
        assertThat(json.get("context").get("current_message").asText()).isEqualTo("你好");
        assertThat(json.get("context").get("open_flow_ids").get(0).asText()).isEqualTo("flow-1");
    }

    @Test
    void shouldDeserializeValidatedPythonResponse() throws Exception {
        String json = """
                {
                  "request_id": "request-1",
                  "schema_version": "1",
                  "relation": "SMALL_TALK",
                  "target_flow_id": null,
                  "intent": "SMALL_TALK",
                  "confidence": 0.95,
                  "reply_draft": "你好，我已经准备好了。"
                }
                """;

        TurnResolutionResponse response = objectMapper.readValue(json, TurnResolutionResponse.class);

        assertThat(response.getRelation()).isEqualTo(RelationType.SMALL_TALK);
        assertThat(response.getConfidence()).isEqualTo(0.95);
        assertThat(validator.validate(response)).isEmpty();
    }

    @Test
    void shouldRejectConfidenceOutsideProtocolRange() {
        TurnResolutionResponse response = new TurnResolutionResponse(
                "request-1",
                "1",
                RelationType.UNKNOWN,
                null,
                "UNKNOWN",
                1.1,
                ""
        );

        assertThat(validator.validate(response))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("confidence"));
    }

    @Test
    void shouldRejectMissingConfidenceAndBlankReply() {
        TurnResolutionResponse response = new TurnResolutionResponse(
                "request-1",
                "1",
                RelationType.UNKNOWN,
                null,
                "UNKNOWN",
                null,
                " "
        );

        assertThat(validator.validate(response))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("confidence", "replyDraft");
    }

    @Test
    void shouldRejectTargetFlowThatConflictsWithRelation() {
        TurnResolutionResponse response = new TurnResolutionResponse(
                "request-1",
                "1",
                RelationType.UNKNOWN,
                "flow-1",
                "UNKNOWN",
                0.5,
                "请补充说明。"
        );

        assertThat(validator.validate(response))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("targetFlowRelationValid");
    }
}
