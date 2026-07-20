package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 持久化调度任务表，当前只支持 REMINDER_DELIVERY。
 */
@Data
@TableName("scheduled_jobs")
public class ScheduledJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型：REMINDER_DELIVERY / HABIT_DELIVERY */
    private String jobType;

    /** 稳定唯一键：REMINDER_DELIVERY:{reminderId}:{nodeType} 或 HABIT_DELIVERY:{habitId}:{occurrenceKey} */
    private String businessKey;

    /** FK → reminders.id，HABIT_DELIVERY 时可空 */
    private Long reminderId;

    /** FK → habit_executions.id，REMINDER_DELIVERY 时可空 */
    private Long habitExecutionId;

    /** 节点类型：PRIMARY/ADVANCE/SNOOZE */
    private String nodeType;

    /** 原计划 UTC 时间 */
    private Instant scheduledAt;

    /** 下一次领取时间 */
    private Instant nextRunAt;

    /** 状态：READY/RUNNING/SUCCEEDED/RETRY_WAIT/FAILED/CANCELLED/MISSED */
    private String status;

    /** 已执行重试次数 */
    private Integer retryCount;

    /** 当前持有者标识 */
    private String leaseOwner;

    /** 租约截止时间 */
    private Instant leaseUntil;

    /** 首次执行创建的幂等 ASSISTANT 消息 ID */
    private Long assistantMessageId;

    /** 最后一次失败错误码（脱敏） */
    private String lastErrorCode;

    private Instant createdAt;

    private Instant updatedAt;
}
