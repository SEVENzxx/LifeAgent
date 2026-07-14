package com.lifeagent.enums;

/**
 * 消息投递状态。
 *
 * <p>PENDING：等待投递；SENT：已成功发送。</p>
 */
public enum DeliveryStatus {
    PENDING("等待投递"),
    SENT("已发送");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
