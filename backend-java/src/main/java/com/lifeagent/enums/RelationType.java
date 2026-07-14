package com.lifeagent.enums;

/**
 * 当前消息与开放流程之间的关系。
 */
public enum RelationType {
    ANSWER_FLOW("回答已有流程"),
    CONFIRM_FLOW("确认已有流程"),
    SMALL_TALK("闲聊"),
    UNKNOWN("无法确定");

    private final String description;

    RelationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前关系是否必须绑定一个开放流程。
     *
     * @return 回答或确认已有流程时返回 true
     */
    public boolean requiresTargetFlow() {
        return this == ANSWER_FLOW || this == CONFIRM_FLOW;
    }
}
