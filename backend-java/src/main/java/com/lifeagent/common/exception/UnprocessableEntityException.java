package com.lifeagent.common.exception;

/**
 * 请求不可处理，如时间超限或查询范围超限。
 */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
