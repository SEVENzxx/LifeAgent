package com.lifeagent.cache.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 习惯候选：30 分钟 Redis 候选，经用户确认后才创建正式 Habit。
 */
@Data
public class HabitDraftCacheValue {

    /** 稳定草稿 token，防重复确认 */
    private String draftToken;

    /** 初始 source_message_id */
    private Long sourceMessageId;

    /** 习惯名称 */
    private String name;

    /** 每日提醒时刻，HH:mm 格式，已排序去重 */
    private List<String> dailyTimes;

    /** 用户本地开始日期 */
    private LocalDate startDate;

    /** 可空结束日期 */
    private LocalDate endDate;

    /** 用户 IANA 时区 */
    private String timezone;

    /** 候选状态：COLLECTING / AWAITING_CONFIRMATION */
    private String draftStatus;

    /** 缺失字段列表 */
    private List<String> missingFields;
}
