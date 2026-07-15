package com.lifeagent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 Mock 和 WeCom 默认关闭时对应路径返回 HTTP 404。
 *
 * <p>使用默认配置（MOCK_CHANNEL_ENABLED=false，WECOM 未启用），
 * Controller 条件注册关闭，请求应得到 404 而非 500。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.profiles.active=default",
    "lifeagent.wecom.enabled=false",
    "lifeagent.mock-channel.enabled=false",
})
class DisabledChannel404Test {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Mock 默认关闭时 POST inbound 返回 404")
    void mockDisabledReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("""
                {"externalMessageId":"x","externalUserId":"x","text":"x","sentAt":"2026-07-14T20:00:00+08:00"}
                """, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/mock/messages/inbound", HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Mock 默认关闭时 GET outbound 返回 404")
    void mockOutboundDisabledReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/mock/messages/outbound?externalUserId=test", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("WeCom 默认关闭时 GET callback 返回 404")
    void weComGetCallbackDisabledReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/channels/wecom/callback?msg_signature=abc&timestamp=1&nonce=abc&echostr=abc",
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("WeCom 默认关闭时 POST callback 返回 404")
    void weComPostCallbackDisabledReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> request = new HttpEntity<>("<xml></xml>", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/channels/wecom/callback?msg_signature=abc&timestamp=1&nonce=abc",
                HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
