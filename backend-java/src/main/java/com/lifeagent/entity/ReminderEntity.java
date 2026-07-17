package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 一次性提醒领域表。
 */
@Data
@TableName("reminders")
public class ReminderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 提醒事项 */
    private String content;

    /** 事件发生时间，可空 */
    private Instant eventAt;

    /** 用户 IANA 时区 */
    private String timezone;

    /** 状态：ACTIVE/DELIVERED/ACKNOWLEDGED/COMPLETED/CANCELLED/FAILED */
    private String status;

    /** 创建来源 USER 消息 ID */
    private Long sourceMessageId;

    /** 乐观锁版本 */
    @Version
    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;
}
