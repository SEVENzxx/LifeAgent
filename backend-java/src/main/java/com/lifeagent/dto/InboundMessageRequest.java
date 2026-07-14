package com.lifeagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Mock 入站消息请求。
 */
@Data
@Schema(description = "Mock 入站消息请求")
public class InboundMessageRequest {

    @NotBlank(message = "externalMessageId 不能为空")
    @Size(max = 100, message = "externalMessageId 不能超过 100 个字符")
    @Schema(description = "外部消息 ID，在 MOCK 渠道内唯一", maxLength = 100)
    private String externalMessageId;

    @NotBlank(message = "externalUserId 不能为空")
    @Size(max = 100, message = "externalUserId 不能超过 100 个字符")
    @Schema(description = "外部用户 ID，映射到内部用户和 Mock 渠道绑定", maxLength = 100)
    private String externalUserId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过 4000 个字符")
    @Schema(description = "消息正文，保留原始 Unicode 内容", maxLength = 4000)
    private String text;

    @NotNull(message = "发送时间不能为空")
    @Schema(description = "消息发送时间，带时区偏移的 ISO-8601 格式")
    private OffsetDateTime sentAt;
}
