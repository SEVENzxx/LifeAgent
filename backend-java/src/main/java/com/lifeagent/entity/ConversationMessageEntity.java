package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户和助手消息表。
 */
@Data
@TableName("conversation_messages")
public class ConversationMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属渠道绑定
     */
    private Long channelBindingId;

    /**
     * 全局幂等键 = channel + ":" + externalMessageId，USER/ASSISTANT 各自唯一
     */
    private String idempotencyKey;

    /**
     * 外部消息 ID，仅作参考
     */
    private String externalMessageId;

    /**
     * 角色：USER、ASSISTANT
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 内容哈希
     */
    private String contentHash;

    /**
     * 投递状态
     */
    private String deliveryStatus;

    /**
     * 消息发送时间：USER 来自渠道声明，ASSISTANT 只在渠道确认成功后写入
     */
    private LocalDateTime sentAt;

    private LocalDateTime createdAt;
}
