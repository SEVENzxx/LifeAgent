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
    @NotBlank(message = "请求 ID 不能为空")
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = "标识不能超过 " + Constants.MAX_IDENTIFIER_LENGTH + " 个字符")
    private String requestId;

    /**
     * 与请求保持一致的协议版本
     */
    @Schema(description = "与请求保持一致的协议版本", pattern = Constants.SCHEMA_VERSION_PATTERN)
    @JsonProperty("schema_version")
    @NotBlank(message = "协议版本不能为空")
    @Pattern(regexp = Constants.SCHEMA_VERSION_PATTERN, message = "协议版本只能包含数字")
    private String schemaVersion;

    /**
     * 当前消息与已有流程之间的关系
     */
    @Schema(description = "当前消息与已有流程之间的关系")
    @NotNull(message = "关系类型不能为空")
    private RelationType relation;

    /**
     * 命中的目标流程 ID，没有命中时为 null
     */
    @Schema(description = "命中的目标流程 ID，没有命中时为空", maxLength = Constants.MAX_IDENTIFIER_LENGTH)
    @JsonProperty("target_flow_id")
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = "标识不能超过 " + Constants.MAX_IDENTIFIER_LENGTH + " 个字符")
    private String targetFlowId;

    /**
     * 经过模型解析和 Python 校验的意图标识
     */
    @Schema(description = "经过模型解析和 Python 校验的意图标识", maxLength = Constants.MAX_IDENTIFIER_LENGTH)
    @NotBlank(message = "意图不能为空")
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = "标识不能超过 " + Constants.MAX_IDENTIFIER_LENGTH + " 个字符")
    private String intent;

    /**
     * 模型置信度，取值范围为 0.0 到 1.0
     */
    @Schema(
            description = "模型置信度，取值范围为 0.0 到 1.0",
            minimum = Constants.MIN_CONFIDENCE_VALUE,
            maximum = Constants.MAX_CONFIDENCE_VALUE
    )
    @NotNull(message = "置信度不能为空")
    @DecimalMin(value = Constants.MIN_CONFIDENCE_VALUE, message = "置信度不能小于 0.0")
    @DecimalMax(value = Constants.MAX_CONFIDENCE_VALUE, message = "置信度不能大于 1.0")
    private Double confidence;

    /**
     * 经过 Python 校验的建议回复，不包含模型厂商原始对象
     */
    @Schema(description = "经过 Python 校验的建议回复，不包含模型厂商原始对象", maxLength = Constants.MAX_MESSAGE_LENGTH)
    @JsonProperty("reply_draft")
    @NotBlank(message = "建议回复不能为空")
    @Size(max = Constants.MAX_MESSAGE_LENGTH, message = "建议回复不能超过 " + Constants.MAX_MESSAGE_LENGTH + " 个字符")
    private String replyDraft;

    /**
     * 校验关系类型与目标流程是否匹配，避免跨服务响应出现自相矛盾的状态。
     *
     * @return 关系与目标流程满足协议约束时返回 true
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "目标流程与关系类型不匹配")
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
