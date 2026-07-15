package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

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
}
