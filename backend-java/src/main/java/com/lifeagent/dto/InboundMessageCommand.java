package com.lifeagent.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * 渠道中立的入站消息参数。
 *
 * <p>Mock 和 WeCom 等渠道统一使用该 DTO 将会话服务与渠道 HTTP 契约解耦。
 * {@code channel} 决定 ASSISTANT 的投递方式：Mock 直接 SENT，WeCom 进入 CREATED 等待异步发送。</p>
 */
@Value
@Builder
public class InboundMessageCommand {

    /** 渠道标识，如 MOCK、WECOM */
    String channel;

    /** 外部用户 ID */
    String externalUserId;

    /** 外部消息 ID，在渠道内唯一 */
    String externalMessageId;

    /** 消息正文 */
    String text;

    /** 渠道声明的消息发送时间 */
    OffsetDateTime sentAt;
}
