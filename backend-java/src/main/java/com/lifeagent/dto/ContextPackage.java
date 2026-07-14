package com.lifeagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifeagent.common.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Java 侧裁剪后发送给 AI 服务的轮次上下文")
public class ContextPackage {

    /**
     * 当前待解析的用户消息
     */
    @Schema(description = "当前待解析的用户消息", maxLength = Constants.MAX_MESSAGE_LENGTH)
    @JsonProperty("current_message")
    @NotBlank(message = Constants.CURRENT_MESSAGE_REQUIRED_MESSAGE)
    @Size(max = Constants.MAX_MESSAGE_LENGTH, message = Constants.CURRENT_MESSAGE_TOO_LONG_MESSAGE)
    private String currentMessage;

    /**
     * 当前仍处于打开状态的流程 ID，最多携带三个
     */
    @Schema(description = "当前仍处于打开状态的流程 ID，最多携带三个")
    @JsonProperty("open_flow_ids")
    @NotNull(message = Constants.OPEN_FLOW_LIST_REQUIRED_MESSAGE)
    @Size(max = Constants.MAX_OPEN_FLOW_COUNT, message = Constants.OPEN_FLOW_COUNT_EXCEEDED_MESSAGE)
    private List<
            @NotBlank(message = Constants.FLOW_ID_REQUIRED_MESSAGE)
            @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = Constants.IDENTIFIER_TOO_LONG_MESSAGE)
            String> openFlowIds;
}
