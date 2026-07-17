package com.lifeagent.enums;

/**
 * 提醒时间来源。
 */
public enum DraftTimeSource {

    USER_EXPLICIT("用户明确表达的时间"),
    AI_SUGGESTED("AI 建议的时间"),
    NONE("没有时间信息");

    private final String description;

    DraftTimeSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
