package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * 最多 5 个最近 ACTIVE/PAUSED 习惯的白名单事实。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecentHabitInfo {

    /** 习惯 ID */
    Long habitId;

    /** 版本号 */
    Integer version;

    /** 习惯名称 */
    String name;

    /** 每日提醒时刻 */
    List<String> dailyTimes;

    /** 状态 */
    String status;
}
