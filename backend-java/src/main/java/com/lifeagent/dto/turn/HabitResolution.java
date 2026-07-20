package com.lifeagent.dto.turn;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/**
 * Python 返回的习惯候选解析结果。
 */
@Value
@Builder
@Jacksonized
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class HabitResolution {

    /** 目标类型：NEW / PENDING_DRAFT / RECENT_HABIT / RECENT_EXECUTION */
    String target;

    /** 操作类型：UPSERT_DRAFT / CONFIRM_DRAFT / ACK / COMPLETE / PAUSE / RESUME / CANCEL */
    String action;

    /** 习惯 ID，Java 提供时才有效 */
    Long habitId;

    /** 习惯版本号 */
    Integer habitVersion;

    /** 习惯名称 */
    String name;

    /** 每日提醒时刻，HH:mm 格式 */
    List<String> dailyTimes;

    /** 开始日期 */
    LocalDate startDate;

    /** 结束日期，可空 */
    LocalDate endDate;

    /** 缺失字段 */
    List<String> missingFields;
}
