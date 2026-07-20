package com.lifeagent.enums;

/**
 * 习惯执行状态。
 */
public enum ExecutionStatus {

    SCHEDULED("已调度"),
    DELIVERED("已发送"),
    ACKNOWLEDGED("已知晓"),
    COMPLETED("已完成"),
    FAILED("发送失败"),
    MISSED("已错过宽限期"),
    CANCELLED("已取消");

    private final String description;

    ExecutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
