package com.lifeagent.enums;

/**
 * 消息角色：USER 为用户原始消息，ASSISTANT 为系统回复。
 */
public enum MessageRole {
    USER("用户"),
    ASSISTANT("助手");

    private final String description;

    MessageRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
