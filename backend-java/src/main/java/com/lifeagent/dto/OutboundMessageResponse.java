package com.lifeagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Mock 出站（已发送）消息响应项。
 */
@Data
@AllArgsConstructor
@Schema(description = "已发送的回复消息")
public class OutboundMessageResponse {

    @Schema(description = "消息 ID")
    private Long messageId;

    @Schema(description = "回复内容")
    private String text;

    @Schema(description = "发送时间")
    private OffsetDateTime sentAt;
}
