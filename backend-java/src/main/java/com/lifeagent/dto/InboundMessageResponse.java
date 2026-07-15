package com.lifeagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 入站消息响应。
 */
@Data
@Builder
@Schema(description = "入站消息响应")
public class InboundMessageResponse {

    @Schema(description = "是否已接受")
    private boolean accepted;

    @Schema(description = "是否重复消息")
    private boolean duplicate;

    @Schema(description = "USER 消息 ID")
    private Long messageId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "渠道绑定 ID")
    private Long bindingId;
}
