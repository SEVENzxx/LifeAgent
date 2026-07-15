package com.lifeagent.common;

import org.springframework.http.ResponseEntity;

/**
 * HTTP 响应构建工具。
 */
public final class ResponseHelper {

    private ResponseHelper() {
    }

    /**
     * 将 {@code (httpStatus, body)} 结果对转换为 {@link ResponseEntity}。
     */
    public static ResponseEntity<String> toResponse(int httpStatus, String body) {
        return ResponseEntity.status(httpStatus).body(body);
    }
}
