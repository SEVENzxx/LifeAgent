package com.lifeagent.enums;

/**
 * 提醒候选目标类型。
 */
public enum DraftTarget {

    NEW("新提醒"),
    PENDING_DRAFT("当前 Redis 待确认候选"),
    RECENT_REMINDER("最近一条正式提醒");

    private final String description;

    DraftTarget(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
