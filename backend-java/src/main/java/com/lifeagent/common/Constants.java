package com.lifeagent.common;

/**
 * 跨文件复用的业务阈值和标识符。
 *
 * <p>单次使用的值直接在调用处内联，不在此维护。</p>
 */
public final class Constants {

    public static final String APPLICATION_NAME = "lifeagent-java";
    public static final String DEFAULT_PROFILE = "default";
    public static final String PROFILE_SEPARATOR = ",";
    public static final String SYSTEM_API_PATH = "/api/v1/system";
    public static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    public static final int MAX_MESSAGE_LENGTH = 4_000;
    public static final int MAX_OPEN_FLOW_COUNT = 3;
    public static final int MAX_IDENTIFIER_LENGTH = 100;

    public static final String SCHEMA_VERSION_PATTERN = "^\\d+$";
    public static final String MIN_CONFIDENCE_VALUE = "0.0";
    public static final String MAX_CONFIDENCE_VALUE = "1.0";

    // ========== 企业微信 ==========

    /** 回调 URL 验证 / 消息接收允许的最大时间戳偏差（秒） */
    public static final long WECOM_MAX_TIMESTAMP_DRIFT_SECONDS = 300;

    /** POST 回调请求体上限（字节） */
    public static final int WECOM_MAX_BODY_SIZE = 64 * 1024;

    /** WeCom 渠道标识 */
    public static final String CHANNEL_WECOM = "WECOM";

    // ========== AI / LLM ==========

    /** AI 服务基础 URL */
    public static final String AI_BASE_URL_DEFAULT = "http://localhost:8000";

    /** AI 连接超时（毫秒） */
    public static final int AI_CONNECT_TIMEOUT_MS = 2000;

    /** AI 读取超时（毫秒） */
    public static final int AI_READ_TIMEOUT_MS = 15000;

    /** Turn Resolver 路径 */
    public static final String AI_TURN_RESOLVE_PATH = "/internal/v1/turns/resolve";

    /** 内部令牌 Header */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    /** 上下文配置默认值 */
    public static final int CONTEXT_RECENT_MAX_MESSAGES = 12;
    public static final int CONTEXT_RECENT_MAX_CHARS = 6000;
    public static final int CONTEXT_SUMMARY_TRIGGER_MESSAGES = 20;
    public static final int CONTEXT_SUMMARY_TRIGGER_CHARS = 8000;
    public static final int CONTEXT_SUMMARY_MAX_CHARS = 1500;
    public static final long CONTEXT_CACHE_TTL_SECONDS = 86400; // 24h

    /** 默认 schema version */
    public static final String SCHEMA_VERSION = "1";

    /** Intent */
    public static final String INTENT_SMALL_TALK = "SMALL_TALK";
    public static final String INTENT_REMINDER_CREATE = "REMINDER_CREATE";
    public static final String INTENT_HABIT_CREATE = "HABIT_CREATE";
    public static final String INTENT_PLAN_CREATE = "PLAN_CREATE";
    public static final String INTENT_ANALYSIS_REQUEST = "ANALYSIS_REQUEST";

    /** Resolution status */
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION";
    public static final String STATUS_AI_UNAVAILABLE = "AI_UNAVAILABLE";

    /** 业务意图统一回复 */
    public static final String REPLY_FEATURE_IN_DEVELOPMENT = "该功能正在开发中，暂时还不能替你完成。";
    public static final String REPLY_AI_FALLBACK = "AI 小助理暂时开小差了，请稍后再试。";

    /** 最低置信度阈值 */
    public static final double MIN_CONFIDENCE = 0.6;

    /** Redis key 前缀：上下文缓存 */
    public static final String REDIS_CONTEXT_PREFIX = "lifeagent:conversation:context:";

    /** Redis key 前缀：提醒候选 */
    public static final String REDIS_REMINDER_DRAFT_PREFIX = "lifeagent:reminder:draft:";

    // ========== LA-005 提醒 & 调度 ==========

    /** 提醒扫描间隔（毫秒） */
    public static final long REMINDER_SCAN_INTERVAL_MS = 5000;

    /** Job 每批最大领取数 */
    public static final int REMINDER_JOB_BATCH_SIZE = 20;

    /** Job 租约时长（秒） */
    public static final long REMINDER_JOB_LEASE_SECONDS = 60;

    /** 最大重试次数 */
    public static final int REMINDER_MAX_RETRIES = 3;

    /** 重试延迟（分钟）：第 1 次、第 2 次、第 3 次 */
    public static final long[] REMINDER_RETRY_DELAYS_MINUTES = {1, 5, 15};

    /** 超过宽限时间（分钟）后标记为 MISSED */
    public static final long REMINDER_LATE_GRACE_MINUTES = 30;

    /** Redis 候选 TTL（分钟） */
    public static final long REMINDER_DRAFT_TTL_MINUTES = 30;

    /** 提醒写入最低置信度 */
    public static final double REMINDER_WRITE_MIN_CONFIDENCE = 0.8;

    /** 提醒 Power 文案前缀 */
    public static final String REMINDER_MESSAGE_PREFIX = "提醒你：";

    /** 提醒目标类型 */
    public static final String DRAFT_TARGET_NEW = "NEW";
    public static final String DRAFT_TARGET_PENDING_DRAFT = "PENDING_DRAFT";
    public static final String DRAFT_TARGET_RECENT_REMINDER = "RECENT_REMINDER";

    /** 提醒操作类型 */
    public static final String DRAFT_ACTION_UPSERT_DRAFT = "UPSERT_DRAFT";
    public static final String DRAFT_ACTION_CONFIRM_DRAFT = "CONFIRM_DRAFT";
    public static final String DRAFT_ACTION_CREATE = "CREATE";
    public static final String DRAFT_ACTION_MODIFY = "MODIFY";
    public static final String DRAFT_ACTION_ACK = "ACK";
    public static final String DRAFT_ACTION_COMPLETE = "COMPLETE";
    public static final String DRAFT_ACTION_SNOOZE = "SNOOZE";
    public static final String DRAFT_ACTION_CANCEL = "CANCEL";

    /** 时间来源 */
    public static final String TIME_SOURCE_USER_EXPLICIT = "USER_EXPLICIT";
    public static final String TIME_SOURCE_AI_SUGGESTED = "AI_SUGGESTED";
    public static final String TIME_SOURCE_NONE = "NONE";

    private Constants() {
    }
}
