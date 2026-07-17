package com.lifeagent.enums;

/**
 * 提醒候选操作类型。
 */
public enum DraftAction {

    UPSERT_DRAFT("新增或修改候选"),
    CONFIRM_DRAFT("确认候选"),
    CREATE("直接创建新提醒"),
    MODIFY("修改现有提醒"),
    ACK("已知晓"),
    COMPLETE("已完成"),
    SNOOZE("稍后提醒"),
    CANCEL("取消");

    private final String description;

    DraftAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
