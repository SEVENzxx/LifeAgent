package com.lifeagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifeagent.common.Constants;
import com.lifeagent.enums.RelationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "轮次解析响应")
public class TurnResolutionResponse {

    /**
     * 与请求保持一致的唯一请求 ID
     */
    @Schema(description = "与请求保持一致的唯一请求 ID", maxLength = Constants.MAX_IDENTIFIER_LENGTH)
    @JsonProperty("request_id")
    @NotBlank(message = Constants.REQUEST_ID_REQUIRED_MESSAGE)
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = Constants.IDENTIFIER_TOO_LONG_MESSAGE)
    private String requestId;

    /**
     * 与请求保持一致的协议版本
     */
    @Schema(description = "与请求保持一致的协议版本", pattern = Constants.SCHEMA_VERSION_PATTERN)
    @JsonProperty("schema_version")
    @NotBlank(message = Constants.SCHEMA_VERSION_REQUIRED_MESSAGE)
    @Pattern(regexp = Constants.SCHEMA_VERSION_PATTERN, message = Constants.SCHEMA_VERSION_INVALID_MESSAGE)
    private String schemaVersion;

    /**
     * 当前消息与已有流程之间的关系
     */
    @Schema(description = "当前消息与已有流程之间的关系")
    @NotNull(message = Constants.RELATION_TYPE_REQUIRED_MESSAGE)
    private RelationType relation;

    /**
     * 命中的目标流程 ID，没有命中时为 null
     */
    @Schema(description = "命中的目标流程 ID，没有命中时为空", maxLength = Constants.MAX_IDENTIFIER_LENGTH)
    @JsonProperty("target_flow_id")
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = Constants.IDENTIFIER_TOO_LONG_MESSAGE)
    private String targetFlowId;

    /**
     * 经过模型解析和 Python 校验的意图标识
     */
    @Schema(description = "经过模型解析和 Python 校验的意图标识", maxLength = Constants.MAX_IDENTIFIER_LENGTH)
    @NotBlank(message = Constants.INTENT_REQUIRED_MESSAGE)
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = Constants.IDENTIFIER_TOO_LONG_MESSAGE)
    private String intent;

    /**
     * 模型置信度，取值范围为 0.0 到 1.0
     */
    @Schema(
            description = "模型置信度，取值范围为 0.0 到 1.0",
            minimum = Constants.MIN_CONFIDENCE_VALUE,
            maximum = Constants.MAX_CONFIDENCE_VALUE
    )
    @NotNull(message = Constants.CONFIDENCE_REQUIRED_MESSAGE)
    @DecimalMin(value = Constants.MIN_CONFIDENCE_VALUE, message = Constants.CONFIDENCE_TOO_LOW_MESSAGE)
    @DecimalMax(value = Constants.MAX_CONFIDENCE_VALUE, message = Constants.CONFIDENCE_TOO_HIGH_MESSAGE)
    private Double confidence;

    /**
     * 经过 Python 校验的建议回复，不包含模型厂商原始对象
     */
    @Schema(description = "经过 Python 校验的建议回复，不包含模型厂商原始对象", maxLength = Constants.MAX_MESSAGE_LENGTH)
    @JsonProperty("reply_draft")
    @NotBlank(message = Constants.REPLY_REQUIRED_MESSAGE)
    @Size(max = Constants.MAX_MESSAGE_LENGTH, message = Constants.REPLY_TOO_LONG_MESSAGE)
    private String replyDraft;

    /**
     * 校验关系类型与目标流程是否匹配，避免跨服务响应出现自相矛盾的状态。
     *
     * @return 关系与目标流程满足协议约束时返回 true
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = Constants.TARGET_FLOW_RELATION_INVALID_MESSAGE)
    public boolean isTargetFlowRelationValid() {
        if (relation == null) {
            return true;
        }
        if (relation.requiresTargetFlow()) {
            return targetFlowId != null && !targetFlowId.isBlank();
        }
        return targetFlowId == null;
    }
}
