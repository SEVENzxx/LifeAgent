package com.lifeagent.wecom;

import com.lifeagent.config.WeComProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * WeComApiClient 单元测试。
 *
 * <p>使用 MockRestServiceServer 模拟企业微信 HTTP API，
 * 验证 token 获取和消息发送请求。</p>
 */
@ExtendWith(MockitoExtension.class)
class WeComApiClientTest {

    private static final String CORP_ID = "test-corp-id";
    private static final String AGENT_ID = "1000001";
    private static final String CORP_SECRET = "test-corp-secret";
    private static final String API_BASE = "https://qyapi.weixin.qq.com";

    private MockRestServiceServer mockServer;
    private RestTemplate restTemplate;
    private WeComApiClient apiClient;
    private WeComProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WeComProperties();
        properties.setEnabled(true);
        properties.setCorpId(CORP_ID);
        properties.setAgentId(AGENT_ID);
        properties.setCorpSecret(CORP_SECRET);
        properties.setCallbackToken("test-token");
        properties.setEncodingAesKey("jWmYm7qr5FMoAMwV8KNiK8YY6GGL3GjH1jTz1Jc4C4T");
        properties.setApiBaseUrl(API_BASE);

        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        apiClient = new WeComApiClient(properties, restTemplate);
    }

    @Test
    @DisplayName("获取 token 后发送文本消息成功返回 true")
    void sendTextMessageSuccess() {
        // 模拟 token 接口
        mockServer.expect(requestTo(API_BASE + "/cgi-bin/gettoken?corpid=" + CORP_ID + "&corpsecret=" + CORP_SECRET))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"access_token\":\"test-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        // 模拟发送接口
        mockServer.expect(requestTo(API_BASE + "/cgi-bin/message/send?access_token=test-token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.touser").value("user001"))
                .andExpect(jsonPath("$.msgtype").value("text"))
                .andExpect(jsonPath("$.agentid").value(1000001))
                .andExpect(jsonPath("$.text.content").value("收到，我已经记录这条消息。"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        boolean result = apiClient.sendTextMessage("user001", "收到，我已经记录这条消息。");

        assertTrue(result);
        mockServer.verify();
    }

    @Test
    @DisplayName("企业微信返回非零 errcode 时返回 false")
    void nonZeroErrcodeReturnsFalse() {
        mockServer.expect(requestTo(API_BASE + "/cgi-bin/gettoken?corpid=" + CORP_ID + "&corpsecret=" + CORP_SECRET))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"access_token\":\"test-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(API_BASE + "/cgi-bin/message/send?access_token=test-token"))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}",
                        MediaType.APPLICATION_JSON));

        boolean result = apiClient.sendTextMessage("user001", "hello");

        assertFalse(result);
        mockServer.verify();
    }

    @Test
    @DisplayName("获取 token 失败时返回 false 不调用发送接口")
    void tokenFailureReturnsFalse() {
        mockServer.expect(requestTo(API_BASE + "/cgi-bin/gettoken?corpid=" + CORP_ID + "&corpsecret=" + CORP_SECRET))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid cred\"}",
                        MediaType.APPLICATION_JSON));

        boolean result = apiClient.sendTextMessage("user001", "hello");

        assertFalse(result);
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 异常时返回 false")
    void httpErrorReturnsFalse() {
        mockServer.expect(requestTo(API_BASE + "/cgi-bin/gettoken?corpid=" + CORP_ID + "&corpsecret=" + CORP_SECRET))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"access_token\":\"test-token\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(API_BASE + "/cgi-bin/message/send?access_token=test-token"))
                .andRespond(withServerError());

        boolean result = apiClient.sendTextMessage("user001", "hello");

        assertFalse(result);
        mockServer.verify();
    }
}
