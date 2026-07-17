package com.lifeagent.enums;

/**
 * 任务类型。LA-005 只支持 REMINDER_DELIVERY。
 */
public enum JobType {

    REMINDER_DELIVERY("提醒投递");

    private final String description;

    JobType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
