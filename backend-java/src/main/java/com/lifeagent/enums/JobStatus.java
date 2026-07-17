package com.lifeagent.enums;

/**
 * 调度任务状态。
 */
public enum JobStatus {

    READY("等待领取"),
    RUNNING("已领取，正在执行"),
    SUCCEEDED("执行成功"),
    RETRY_WAIT("等待重试"),
    FAILED("重试耗尽，最终失败"),
    CANCELLED("被取消"),
    MISSED("超过宽限时间未执行");

    private final String description;

    JobStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
