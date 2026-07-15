package com.lifeagent.enums;

/**
 * 消息状态。
 *
 * <p>USER 始终为 CREATED（已收到）；ASSISTANT 从 CREATED 流转到 SENT/FAILED。</p>
 */
public enum DeliveryStatus {
    CREATED("已创建"),
    SENT("已成功发送"),
    FAILED("发送失败");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
