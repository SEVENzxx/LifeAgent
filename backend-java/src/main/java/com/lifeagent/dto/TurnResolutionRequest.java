package com.lifeagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifeagent.common.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
@Schema(description = "轮次解析请求")
public class TurnResolutionRequest {

    /**
     * 本次跨服务调用的唯一请求 ID
     */
    @Schema(description = "本次跨服务调用的唯一请求 ID", maxLength = Constants.MAX_IDENTIFIER_LENGTH)
    @JsonProperty("request_id")
    @NotBlank(message = "请求 ID 不能为空")
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = "标识不能超过 " + Constants.MAX_IDENTIFIER_LENGTH + " 个字符")
    private String requestId;

    /**
     * Java 与 Python 共同维护的协议版本
     */
    @Schema(description = "Java 与 Python 共同维护的协议版本", pattern = Constants.SCHEMA_VERSION_PATTERN)
    @JsonProperty("schema_version")
    @NotBlank(message = "协议版本不能为空")
    @Pattern(regexp = Constants.SCHEMA_VERSION_PATTERN, message = "协议版本只能包含数字")
    private String schemaVersion;

    /**
     * 经过 Java 侧裁剪后的模型上下文
     */
    @Schema(description = "经过 Java 侧裁剪后的模型上下文")
    @NotNull(message = "上下文不能为空")
    @Valid
    private ContextPackage context;
}
