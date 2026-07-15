package com.lifeagent.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * 渠道中立的入站消息参数，由 WeCom 等渠道构建并委托给 {@link com.lifeagent.service.ConversationService}。
 */
@Value
@Builder
public class InboundMessageCommand {

    /** 渠道标识，如 WECOM */
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
