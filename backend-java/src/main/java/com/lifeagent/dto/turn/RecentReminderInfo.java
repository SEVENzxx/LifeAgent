package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 最多一个最近明确 Reminder 的白名单事实。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecentReminderInfo {

    /** Reminder ID */
    Long reminderId;

    /** 版本号 */
    Integer version;

    /** 状态 */
    String status;

    /** 事项 */
    String content;

    /** 事件 UTC 时间 */
    Instant eventAt;

    /** 主提醒 UTC 时间 */
    Instant remindAt;

    /** 最近发送时间 */
    Instant lastSentAt;
}
