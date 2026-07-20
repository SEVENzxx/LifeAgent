package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/**
 * 最多一个未过期 Redis 习惯候选的白名单字段。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PendingHabitInfo {

    /** 草稿 token */
    String draftToken;

    /** 习惯名称 */
    String name;

    /** 每日提醒时刻 */
    List<String> dailyTimes;

    /** 开始日期 */
    LocalDate startDate;

    /** 结束日期，可空 */
    LocalDate endDate;

    /** 用户时区 */
    String timezone;

    /** 候选状态 */
    String draftStatus;
}
