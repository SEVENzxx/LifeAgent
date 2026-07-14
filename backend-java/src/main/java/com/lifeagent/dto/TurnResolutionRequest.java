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
    @NotBlank(message = Constants.REQUEST_ID_REQUIRED_MESSAGE)
    @Size(max = Constants.MAX_IDENTIFIER_LENGTH, message = Constants.IDENTIFIER_TOO_LONG_MESSAGE)
    private String requestId;

    /**
     * Java 与 Python 共同维护的协议版本
     */
    @Schema(description = "Java 与 Python 共同维护的协议版本", pattern = Constants.SCHEMA_VERSION_PATTERN)
    @JsonProperty("schema_version")
    @NotBlank(message = Constants.SCHEMA_VERSION_REQUIRED_MESSAGE)
    @Pattern(regexp = Constants.SCHEMA_VERSION_PATTERN, message = Constants.SCHEMA_VERSION_INVALID_MESSAGE)
    private String schemaVersion;

    /**
     * 经过 Java 侧裁剪后的模型上下文
     */
    @Schema(description = "经过 Java 侧裁剪后的模型上下文")
    @NotNull(message = Constants.CONTEXT_REQUIRED_MESSAGE)
    @Valid
    private ContextPackage context;
}
