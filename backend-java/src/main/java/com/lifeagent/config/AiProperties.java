package com.lifeagent.config;

import com.lifeagent.common.Constants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI / LLM 相关配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lifeagent.ai")
public class AiProperties {

    /** AI 服务基础 URL */
    private String baseUrl = Constants.AI_BASE_URL_DEFAULT;

    /** 内部服务令牌 */
    private String internalToken = "";

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = Constants.AI_CONNECT_TIMEOUT_MS;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = Constants.AI_READ_TIMEOUT_MS;

    /** 近期窗口最大消息数 */
    private int recentMaxMessages = Constants.CONTEXT_RECENT_MAX_MESSAGES;

    /** 近期窗口最大字符数 */
    private int recentMaxChars = Constants.CONTEXT_RECENT_MAX_CHARS;

    /** 摘要触发消息数 */
    private int summaryTriggerMessages = Constants.CONTEXT_SUMMARY_TRIGGER_MESSAGES;

    /** 摘要触发字符数 */
    private int summaryTriggerChars = Constants.CONTEXT_SUMMARY_TRIGGER_CHARS;

    /** 摘要最大字符数 */
    private int summaryMaxChars = Constants.CONTEXT_SUMMARY_MAX_CHARS;

    /** 上下文缓存 TTL（秒） */
    private long contextCacheTtlSeconds = Constants.CONTEXT_CACHE_TTL_SECONDS;

    // ========== LA-005 提醒 & 调度 ==========

    /** 提醒扫描间隔（毫秒） */
    private long reminderScanIntervalMs = Constants.REMINDER_SCAN_INTERVAL_MS;

    /** Job 每批最大领取数 */
    private int reminderJobBatchSize = Constants.REMINDER_JOB_BATCH_SIZE;

    /** Job 租约时长（秒） */
    private long reminderJobLeaseSeconds = Constants.REMINDER_JOB_LEASE_SECONDS;

    /** 最大重试次数 */
    private int reminderMaxRetries = Constants.REMINDER_MAX_RETRIES;

    /** 重试延迟（逗号分隔） */
    private String reminderRetryDelaysMinutes = "1m,5m,15m";

    /** 超过宽限时间（分钟） */
    private long reminderLateGraceMinutes = Constants.REMINDER_LATE_GRACE_MINUTES;

    /** Redis 候选 TTL（分钟） */
    private long reminderDraftTtlMinutes = Constants.REMINDER_DRAFT_TTL_MINUTES;

    /** 提醒写入最低置信度 */
    private double reminderWriteMinConfidence = Constants.REMINDER_WRITE_MIN_CONFIDENCE;
}
