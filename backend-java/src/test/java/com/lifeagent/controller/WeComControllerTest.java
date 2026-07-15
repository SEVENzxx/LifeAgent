package com.lifeagent.controller;

import com.lifeagent.dto.WeComCallbackParams;
import com.lifeagent.wecom.WeComCallbackResult;
import com.lifeagent.wecom.WeComCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * WeCom 回调控制器单元测试。
 *
 * <p>验证 controller 正确委托给 {@link WeComCallbackService} 并映射 HTTP 响应。
 * 业务验证逻辑的完整测试见 {@code WeComCallbackServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class WeComControllerTest {

    @Mock
    private WeComCallbackService callbackService;

    private WeComController controller;

    @BeforeEach
    void setUp() {
        controller = new WeComController(callbackService);
    }

    @Test
    @DisplayName("GET 回调验证：委托 service 并返回 200")
    void verifyUrlSuccess() {
        when(callbackService.verifyUrl(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(200, "decrypted-content"));

        ResponseEntity<String> response = controller.verifyUrl(
                new WeComCallbackParams("sig", "123", "nonce"), "enc");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("decrypted-content", response.getBody());
    }

    @Test
    @DisplayName("GET 回调验证：委托 service 并映射 403")
    void verifyUrlForbidden() {
        when(callbackService.verifyUrl(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(403, "签名验证失败"));

        ResponseEntity<String> response = controller.verifyUrl(
                new WeComCallbackParams("bad-sig", "123", "nonce"), "enc");

        assertEquals(403, response.getStatusCode().value());
        assertEquals("签名验证失败", response.getBody());
    }

    @Test
    @DisplayName("GET 回调验证：委托 service 并映射 400")
    void verifyUrlBadRequest() {
        when(callbackService.verifyUrl(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(400, "参数不完整"));

        ResponseEntity<String> response = controller.verifyUrl(
                new WeComCallbackParams("", "123", "nonce"), "enc");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("参数不完整", response.getBody());
    }

    @Test
    @DisplayName("POST 回调接收：委托 service 并返回 200")
    void receiveMessageSuccess() {
        when(callbackService.receiveMessage(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(200, "success"));

        ResponseEntity<String> response = controller.receiveMessage(
                new WeComCallbackParams("sig", "123", "nonce"), "<xml/>");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", response.getBody());
    }

    @Test
    @DisplayName("POST 回调接收：委托 service 并映射 503")
    void receiveMessageServiceUnavailable() {
        when(callbackService.receiveMessage(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(503, "服务暂时不可用"));

        ResponseEntity<String> response = controller.receiveMessage(
                new WeComCallbackParams("sig", "123", "nonce"), "<xml/>");

        assertEquals(503, response.getStatusCode().value());
        assertEquals("服务暂时不可用", response.getBody());
    }

    @Test
    @DisplayName("POST 回调接收：委托 service 并映射 403")
    void receiveMessageForbidden() {
        when(callbackService.receiveMessage(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(403, "签名验证失败"));

        ResponseEntity<String> response = controller.receiveMessage(
                new WeComCallbackParams("bad-sig", "123", "nonce"), "<xml/>");

        assertEquals(403, response.getStatusCode().value());
        assertEquals("签名验证失败", response.getBody());
    }

    @Test
    @DisplayName("POST 回调接收：委托 service 并映射 400")
    void receiveMessageBadRequest() {
        when(callbackService.receiveMessage(any(WeComCallbackParams.class), anyString()))
                .thenReturn(new WeComCallbackResult(400, "消息数据不完整"));

        ResponseEntity<String> response = controller.receiveMessage(
                new WeComCallbackParams("sig", "123", "nonce"), "");

        assertEquals(400, response.getStatusCode().value());
        assertEquals("消息数据不完整", response.getBody());
    }
}
