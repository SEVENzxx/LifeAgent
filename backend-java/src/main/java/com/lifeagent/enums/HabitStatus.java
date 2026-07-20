package com.lifeagent.enums;

/**
 * 日常习惯状态。
 */
public enum HabitStatus {

    ACTIVE("生效中"),
    PAUSED("已暂停"),
    CANCELLED("已取消"),
    ENDED("已结束，超过结束日期");

    private final String description;

    HabitStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
