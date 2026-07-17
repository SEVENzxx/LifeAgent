package com.lifeagent.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 草稿 Token 对应的候选快照，用于确认时校验一致性。
 */
@Data
public class ReminderDraftEntity {

    /** 稳定草稿 token，防重复确认 */
    private String draftToken;

    /** 初始 source_message_id */
    private Long sourceMessageId;

    /** 目标确认时的 Reminder ID（修改现有提醒时） */
    private Long targetReminderId;

    /** 目标 Reminder 版本号 */
    private Integer targetReminderVersion;

    /** 事项 */
    private String content;

    /** 事件 UTC 时间 */
    private Instant eventAt;

    /** 主提醒候选 UTC 时间 */
    private Instant remindAt;

    /** 提前提醒 UTC 时间 */
    private Instant advanceRemindAt;

    /** 用户时区 */
    private String timezone;

    /** 时间来源 */
    private String timeSource;

    /** 候选状态：COLLECTING / AWAITING_CONFIRMATION */
    private String draftStatus;
}
