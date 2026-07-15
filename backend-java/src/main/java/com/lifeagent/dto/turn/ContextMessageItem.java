package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * 近期消息或待摘要消息项。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ContextMessageItem {

    /** 消息 ID */
    Long messageId;

    /** 角色：USER、ASSISTANT */
    String role;

    /** 消息正文 */
    String content;
}
