package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Python 返回给 Java 的轮次解析结果。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TurnResolutionResponse {

    /** 必须与请求一致 */
    String requestId;

    /** 必须与请求一致 */
    String schemaVersion;

    /** RESOLVED / NEEDS_CLARIFICATION / AI_UNAVAILABLE */
    String resolutionStatus;

    /** 五种 Intent 之一：SMALL_TALK / REMINDER_CREATE / HABIT_CREATE / PLAN_CREATE / ANALYSIS_REQUEST */
    String intent;

    /** 0～1 */
    double confidence;

    /** 回复草稿，AI 不可用时为空 */
    String replyDraft;

    /** 仅摘要成功时返回，最长 1,500 */
    String updatedSummary;

    /** LA-005 提醒候选解析结果，可空 */
    ReminderResolution reminderResolution;
}
