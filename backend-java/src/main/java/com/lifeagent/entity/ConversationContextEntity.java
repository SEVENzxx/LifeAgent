package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 对话上下文：每用户一行的滚动摘要和最近语义状态。
 */
@Data
@TableName("conversation_contexts")
public class ConversationContextEntity {

    /**
     * 用户 ID，主键，手动赋值不使用自增
     */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /**
     * 滚动摘要，最长 1,500 字符
     */
    private String summary;

    /**
     * Java 控制的摘要截止消息 ID
     */
    private Long summarizedThroughMessageId;

    /**
     * 最近有效 Intent
     */
    private String lastIntent;

    /**
     * 最近语义识别置信度，0～1
     */
    private BigDecimal lastConfidence;

    /**
     * 最近有效语义识别对应的 USER 消息 ID
     */
    private Long lastResolvedMessageId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
