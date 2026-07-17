package com.lifeagent.enums;

/**
 * 提醒节点类型。
 */
public enum NodeType {

    PRIMARY("主节点"),
    ADVANCE("提前节点"),
    SNOOZE("稍后提醒节点");

    private final String description;

    NodeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
