package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 最多一个未过期 Redis 提醒候选的白名单字段。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PendingReminderInfo {

    /** 草稿 token */
    String draftToken;

    /** 候选事项 */
    String content;

    /** 事件 UTC 时间 */
    Instant eventAt;

    /** 主提醒候选 UTC 时间 */
    Instant remindAt;

    /** 提前提醒 UTC 时间 */
    Instant advanceRemindAt;

    /** 时间来源 */
    String timeSource;

    /** 候选状态 */
    String draftStatus;

    /** 是否有唯一目标 Reminder */
    Long targetReminderId;

    /** 目标 Reminder 版本 */
    Integer targetReminderVersion;
}
