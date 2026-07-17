package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Python 返回的提醒候选解析结果。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ReminderResolution {

    /** 目标类型：NEW / PENDING_DRAFT / RECENT_REMINDER */
    String target;

    /** 操作类型：UPSERT_DRAFT / CONFIRM_DRAFT / CREATE / MODIFY / ACK / COMPLETE / SNOOZE / CANCEL */
    String action;

    /** 候选事项，最长 1,000 */
    String content;

    /** 事件 UTC 时间 */
    Instant eventAt;

    /** 主提醒 UTC 时间 */
    Instant remindAt;

    /** 提前提醒 UTC 时间 */
    Instant advanceRemindAt;

    /** 时间来源：USER_EXPLICIT / AI_SUGGESTED / NONE */
    String timeSource;

    /** 缺失字段 */
    List<String> missingFields;
}
