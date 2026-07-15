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
}
