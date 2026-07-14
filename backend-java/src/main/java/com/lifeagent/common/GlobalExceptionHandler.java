package com.lifeagent.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一将业务异常、参数异常和未知异常转换为标准 API 响应。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理可预期业务异常，并向调用方返回安全的业务原因。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception) {
        log.warn("业务处理失败, exceptionType={}, message={}",
                exception.getClass().getSimpleName(), exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(exception.getMessage()));
    }

    /**
     * 处理 Controller DTO 参数校验异常，只返回首个字段错误。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(Constants.INVALID_REQUEST_MESSAGE);
        log.warn("请求参数校验失败, message={}", message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    /**
     * 处理未预期异常；完整堆栈只在此处记录一次，响应不暴露内部细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        // 未知异常只在统一出口打印完整堆栈，避免同一调用链重复记录异常。
        log.error("服务发生未处理异常, exceptionType={}", exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(Constants.SERVICE_UNAVAILABLE_MESSAGE));
    }
}
