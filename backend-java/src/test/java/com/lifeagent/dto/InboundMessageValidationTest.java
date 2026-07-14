package com.lifeagent.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 入站消息 DTO 校验测试。
 *
 * <p>覆盖必填字段、长度限制和边界条件。</p>
 */
class InboundMessageValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("合法请求无校验错误")
    void validRequestPasses() {
        InboundMessageRequest request = createValidRequest();
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("externalMessageId 为空时校验失败")
    void blankExternalMessageIdFails() {
        InboundMessageRequest request = createValidRequest();
        request.setExternalMessageId("");
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("externalMessageId")));
    }

    @Test
    @DisplayName("externalMessageId 超过 100 字符时校验失败")
    void externalMessageIdTooLongFails() {
        InboundMessageRequest request = createValidRequest();
        request.setExternalMessageId("x".repeat(101));
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("externalMessageId")));
    }

    @Test
    @DisplayName("externalUserId 为空时校验失败")
    void blankExternalUserIdFails() {
        InboundMessageRequest request = createValidRequest();
        request.setExternalUserId("  ");
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("消息内容为空时校验失败")
    void blankTextFails() {
        InboundMessageRequest request = createValidRequest();
        request.setText("");
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("text")));
    }

    @Test
    @DisplayName("消息内容超过 4000 字符时校验失败")
    void textTooLongFails() {
        InboundMessageRequest request = createValidRequest();
        request.setText("x".repeat(4001));
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("sentAt 为空时校验失败")
    void nullSentAtFails() {
        InboundMessageRequest request = createValidRequest();
        request.setSentAt(null);
        Set<ConstraintViolation<InboundMessageRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v ->
                v.getPropertyPath().toString().contains("sentAt")));
    }

    private InboundMessageRequest createValidRequest() {
        InboundMessageRequest request = new InboundMessageRequest();
        request.setExternalMessageId("mock-msg-001");
        request.setExternalUserId("mock-user-001");
        request.setText("你好，这是一条测试消息");
        request.setSentAt(OffsetDateTime.now());
        return request;
    }
}
