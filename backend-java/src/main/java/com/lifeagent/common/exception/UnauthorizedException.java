package com.lifeagent.common.exception;

/**
 * 鉴权失败，设备 Token 缺失或无效。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
