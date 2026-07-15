package com.lifeagent.dto.turn;

import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Java 请求 Python 解析当前轮次的跨服务请求协议。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TurnResolutionRequest {

    /** 本次 AI 调用唯一标识，最长 100 */
    String requestId;

    /** 跨服务协议版本，纯数字 */
    @Pattern(regexp = "^\\d+$")
    String schemaVersion;

    /** Java 侧裁剪并提供的轮次上下文 */
    ContextPackage context;
}
