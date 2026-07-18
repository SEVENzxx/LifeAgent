package com.lifeagent.common.exception;

/**
 * 请求正文冲突，如相同 eventId 但内容不同。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
