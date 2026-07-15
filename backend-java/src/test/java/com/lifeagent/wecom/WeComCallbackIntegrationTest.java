package com.lifeagent.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifeagent.config.WeComCrypto;
import com.lifeagent.config.WeComProperties;
import com.lifeagent.entity.ChannelBindingEntity;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 企业微信 POST 回调集成测试。
 *
 * <p>连接本地 Compose 的 PostgreSQL + Redis，验证真实加密回调路径。
 * 使用 @MockBean 阻止 WeComApiClient 发送真实请求。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "lifeagent.wecom.enabled=true",
                "lifeagent.wecom.corp-id=wx-test-corp-id",
                "lifeagent.wecom.agent-id=1000001",
                "lifeagent.wecom.callback-token=test-callback-token",
                "lifeagent.wecom.encoding-aes-key=jWmYm7qr5FMoAMwV8KNiK8YY6GGL3GjH1jTz1Jc4C4T",
                "lifeagent.wecom.corp-secret=test-corp-secret-for-test",
                "spring.main.allow-bean-definition-overriding=true",
        })
class WeComCallbackIntegrationTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }

    private static final String CORP_ID = "wx-test-corp-id";
    private static final String AGENT_ID = "1000001";
    private static final String TOKEN = "test-callback-token";
    private static final String ENCODING_AES_KEY = "jWmYm7qr5FMoAMwV8KNiK8YY6GGL3GjH1jTz1Jc4C4T";
    private static final String CORP_SECRET = "test-corp-secret-for-test";
    private static final String FROM_USER = "test-user-001";
    private static final String MSG_ID = "1234567890";

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-07-15T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private ChannelBindingMapper channelBindingMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private WeComApiClient weComApiClient;

    private WeComCrypto crypto;

    @BeforeEach
    void setUp() {
        // 异步 AI 任务可能在清理间隙创建 ASSISTANT，循环删除确保完全清空后再删关联表
        while (conversationMessageMapper.selectCount(null) > 0) {
            conversationMessageMapper.delete(null);
        }
        channelBindingMapper.delete(null);
        userMapper.delete(null);

        when(weComApiClient.sendTextMessage(anyString(), anyString()))
                .thenReturn(true);

        crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
    }

    @Test
    @DisplayName("首次加密文本回调：HTTP 200 success，仅创建 WECOM 绑定和 USER(CREATED)")
    void firstEncryptedCallbackCreatesUserOnly() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + FROM_USER + "]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>" + MSG_ID + "</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        ResponseEntity<String> response = sendEncryptedPost(innerXml);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", response.getBody());

        // 同步只持久化 USER 消息，ASSISTANT 由异步 AI 任务创建
        List<ConversationMessageEntity> messages = conversationMessageMapper.selectList(null);
        assertEquals(1, messages.size());

        ConversationMessageEntity userMsg = messages.stream()
                .filter(m -> MessageRole.USER.name().equals(m.getRole()))
                .findFirst().orElseThrow();
        assertEquals("你好", userMsg.getContent());
        assertEquals("WECOM:" + MSG_ID, userMsg.getIdempotencyKey());
        assertEquals(DeliveryStatus.CREATED.name(), userMsg.getDeliveryStatus());

        List<ChannelBindingEntity> bindings = channelBindingMapper.selectList(null);
        assertEquals(1, bindings.size());
        assertEquals("WECOM", bindings.get(0).getChannel());
        assertEquals(FROM_USER, bindings.get(0).getExternalUserId());

        assertEquals(1, userMapper.selectCount(null).intValue());
    }

    @Test
    @DisplayName("重复 MsgId 回调：HTTP 200，不新增 USER")
    void duplicateMsgIdReturnsSuccessNoNewUserRows() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + FROM_USER + "]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>" + MSG_ID + "</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        ResponseEntity<String> firstResp = sendEncryptedPost(innerXml);
        assertEquals(HttpStatus.OK, firstResp.getStatusCode());

        ResponseEntity<String> secondResp = sendEncryptedPost(innerXml);
        assertEquals(HttpStatus.OK, secondResp.getStatusCode());
        assertEquals("success", secondResp.getBody());

        // 验证不新增 USER（异步 AI 可能已创建 ASSISTANT）
        long userCount = conversationMessageMapper.selectCount(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getRole, MessageRole.USER));
        assertEquals(1, userCount);
    }

    @Test
    @DisplayName("不支持的消息类型：HTTP 200，不写库")
    void unsupportedMsgTypeReturns200NoRows() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + FROM_USER + "]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[image]]></MsgType>"
                + "<Content><![CDATA[图片]]></Content>"
                + "<MsgId>" + MSG_ID + "</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        ResponseEntity<String> response = sendEncryptedPost(innerXml);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", response.getBody());
        assertEquals(0, conversationMessageMapper.selectCount(null).intValue());
    }

    @Test
    @DisplayName("非法签名：HTTP 403，不写库")
    void invalidSignatureReturns403NoRows() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + FROM_USER + "]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>" + MSG_ID + "</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        String encrypted = crypto.encryptContent(innerXml, CORP_ID);
        long timestamp = fixedClock.instant().getEpochSecond();
        String wrongSignature = "0000000000000000000000000000000000000000";

        String outerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<AgentID><![CDATA[" + AGENT_ID + "]]></AgentID>"
                + "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>"
                + "</xml>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> request = new HttpEntity<>(outerXml, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/channels/wecom/callback"
                        + "?msg_signature=" + wrongSignature
                        + "&timestamp=" + timestamp
                        + "&nonce=test-nonce",
                HttpMethod.POST, request, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(0, conversationMessageMapper.selectCount(null).intValue());
    }

    private ResponseEntity<String> sendEncryptedPost(String innerXml) {
        String encrypted = crypto.encryptContent(innerXml, CORP_ID);
        long timestamp = fixedClock.instant().getEpochSecond();
        String nonce = "test-nonce-" + System.nanoTime();
        String signature = crypto.generateSignature(String.valueOf(timestamp), nonce, encrypted);

        String outerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<AgentID><![CDATA[" + AGENT_ID + "]]></AgentID>"
                + "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>"
                + "</xml>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> request = new HttpEntity<>(outerXml, headers);

        String url = "/api/v1/channels/wecom/callback"
                + "?msg_signature=" + signature
                + "&timestamp=" + timestamp
                + "&nonce=" + nonce;

        return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }
}
