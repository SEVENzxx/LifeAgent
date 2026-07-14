package com.lifeagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Mock 入站消息响应。
 */
@Data
@AllArgsConstructor
@Schema(description = "Mock 入站消息响应")
public class InboundMessageResponse {

    @Schema(description = "是否已接受")
    private boolean accepted;

    @Schema(description = "是否重复消息")
    private boolean duplicate;

    @Schema(description = "USER 消息 ID")
    private Long messageId;

    @Schema(description = "回复内容")
    private String reply;
}
