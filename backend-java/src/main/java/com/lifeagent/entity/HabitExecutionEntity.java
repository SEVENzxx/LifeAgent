package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/**
 * 习惯执行记录，每个提醒时刻一条。
 */
@Data
@TableName("habit_executions")
public class HabitExecutionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** FK → habits.id */
    private Long habitId;

    /** 本地日期＋时刻稳定键，例如 2026-07-19#09:00 */
    private String occurrenceKey;

    /** 转换后的 UTC 时刻 */
    private Instant scheduledAt;

    /** 状态：SCHEDULED/DELIVERED/ACKNOWLEDGED/COMPLETED/FAILED/MISSED/CANCELLED */
    private String status;

    /** 用户确认完成时间，可空 */
    private Instant completedAt;

    /** 完成来源 USER 消息 ID，可空 */
    private Long completionMessageId;

    /** 乐观锁版本 */
    @Version
    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;
}
