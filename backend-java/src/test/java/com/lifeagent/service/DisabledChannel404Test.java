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
 * 验证 WeCom 关闭时对应路径返回 HTTP 404。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.profiles.active=default",
    "lifeagent.wecom.enabled=false",
})
class DisabledChannel404Test {

    @Autowired
    private TestRestTemplate restTemplate;

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
