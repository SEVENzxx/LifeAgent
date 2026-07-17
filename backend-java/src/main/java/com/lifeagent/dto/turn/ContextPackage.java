package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Java 侧裁剪后发送给 Python 的上下文包。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ContextPackage {

    /** 当前用户消息正文，最长 4,000 */
    String currentMessage;

    /** 已有摘要，可空 */
    String memorySummary;

    /** 近期窗口消息，最多 12 条，正文合计最多 6,000 字符 */
    List<ContextMessageItem> recentMessages;

    /** Java 是否要求生成新摘要 */
    boolean summaryRequested;

    /** 触发摘要时才包含本批待摘要旧消息，否则为空 */
    List<ContextMessageItem> summaryMessages;

    // ========== LA-005 提醒上下文 ==========

    /** Java 当前带偏移 ISO 8601 时间 */
    String referenceTime;

    /** 用户 IANA 时区 */
    String timezone;

    /** 最多一个未过期 Redis 候选，可空 */
    PendingReminderInfo pendingReminder;

    /** 最多一个最近明确 Reminder，可空 */
    RecentReminderInfo recentReminder;
}
