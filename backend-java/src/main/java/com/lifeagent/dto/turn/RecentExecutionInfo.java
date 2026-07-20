package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * 最近一次已发送但尚未完成的执行。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecentExecutionInfo {

    /** 执行 ID */
    Long executionId;

    /** 习惯 ID */
    Long habitId;

    /** 发生键 */
    String occurrenceKey;

    /** 状态 */
    String status;
}
