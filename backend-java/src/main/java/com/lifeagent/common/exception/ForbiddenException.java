package com.lifeagent.common.exception;

/**
 * 设备凭证错误或已撤销。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
