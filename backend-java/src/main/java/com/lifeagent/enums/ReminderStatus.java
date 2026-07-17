package com.lifeagent.enums;

/**
 * 提醒状态。
 */
public enum ReminderStatus {

    ACTIVE("生效中，仍有未来或可重试节点"),
    DELIVERED("最后一个节点已明确发送成功"),
    ACKNOWLEDGED("用户已知晓"),
    COMPLETED("用户标记为已完成"),
    CANCELLED("用户已取消"),
    FAILED("不存在可执行节点且最终投递失败");

    private final String description;

    ReminderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
