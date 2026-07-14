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
    @NotBlank(message = "当前消息不能为空")
    @Size(max = Constants.MAX_MESSAGE_LENGTH, message = "当前消息不能超过 " + Constants.MAX_MESSAGE_LENGTH + " 个字符")
    private String currentMessage;

    /**
     * 当前仍处于打开状态的流程 ID，最多携带三个
     */
    @Schema(description = "当前仍处于打开状态的流程 ID，最多携带三个")
    @JsonProperty("open_flow_ids")
    @NotNull(message = "打开流程列表不能为空")
    @Size(max = Constants.MAX_OPEN_FLOW_COUNT, message = "打开流程不能超过三个")
    private List<
            @NotBlank(message = "流程 ID 不能为空")
            @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = "标识不能超过 " + Constants.MAX_IDENTIFIER_LENGTH + " 个字符")
            String> openFlowIds;
}
