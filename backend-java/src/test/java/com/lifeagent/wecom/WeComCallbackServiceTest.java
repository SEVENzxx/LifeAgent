package com.lifeagent.wecom;

import com.lifeagent.ai.ReplyService;
import com.lifeagent.config.AsyncConfig;
import com.lifeagent.config.WeComCrypto;
import com.lifeagent.config.WeComProperties;
import com.lifeagent.dto.InboundMessageCommand;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WeComCallbackService 单元测试。
 *
 * <p>使用真实 WeComCrypto 和 WeComXmlParser 验证签名、解密、字段校验逻辑，
 * ConversationService 使用 Mock 避免写库。</p>
 */
@ExtendWith(MockitoExtension.class)
class WeComCallbackServiceTest {

    private static final String CORP_ID = "wx5823bf96d3bd56c7";
    private static final String TOKEN = "QDG6eK";
    private static final String ENCODING_AES_KEY = "jWmYm7qr5FMoAMwV8KNiK8YY6GGL3GjH1jTz1Jc4C4T";
    private static final String AGENT_ID = "1000001";
    private static final String CORP_SECRET = "test-corp-secret";

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-07-15T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ConversationService conversationService;

    @Mock
    private ReplyService replyService;

    @Mock
    private ConversationMessageMapper conversationMessageMapper;

    @Mock
    private WeComApiClient weComApiClient;

    @Captor
    private ArgumentCaptor<InboundMessageCommand> commandCaptor;

    private WeComCallbackService service;
    private WeComCrypto crypto;
    private WeComProperties properties;
    private ExecutorService aiTaskExecutor;

    @BeforeEach
    void setUp() {
        properties = new WeComProperties();
        properties.setEnabled(true);
        properties.setCorpId(CORP_ID);
        properties.setAgentId(AGENT_ID);
        properties.setCallbackToken(TOKEN);
        properties.setEncodingAesKey(ENCODING_AES_KEY);
        properties.setCorpSecret(CORP_SECRET);

        aiTaskExecutor = Executors.newSingleThreadExecutor();
        service = new WeComCallbackService(properties, conversationService, replyService, fixedClock);
        service.initCrypto();
        ReflectionTestUtils.setField(service, "aiTaskExecutor", aiTaskExecutor);
        crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
    }

    // ==================== GET 回调验证 ====================

    @Test
    @DisplayName("GET 回调验证：正确签名和解密返回 200 和明文")
    void verifyUrlSuccess() {
        String plaintext = "<xml><Content>hello</Content></xml>";
        long timestamp = Instant.now(fixedClock).getEpochSecond();
        String ts = String.valueOf(timestamp);
        String nonce = "1372623149";

        String encrypted = crypto.encryptContent(plaintext, CORP_ID);
        String signature = crypto.generateSignature(ts, nonce, encrypted);

        WeComCallbackResult result = service.verifyUrl(signature, ts, nonce, encrypted);

        assertEquals(200, result.getHttpStatus());
        String normalizedExpected = plaintext.replaceAll("\\s+", "");
        String normalizedResult = result.getBody().replaceAll("\\s+", "");
        assertEquals(normalizedExpected, normalizedResult);
    }

    @Test
    @DisplayName("GET 回调验证：缺少参数返回 400")
    void missingParametersReturns400() {
        WeComCallbackResult result = service.verifyUrl("", "123", "nonce", "echostr");
        assertEquals(400, result.getHttpStatus());
    }

    @Test
    @DisplayName("GET 回调验证：所有参数为空返回 400")
    void allBlankReturns400() {
        WeComCallbackResult result = service.verifyUrl("", "", "", "");
        assertEquals(400, result.getHttpStatus());
    }

    @Test
    @DisplayName("GET 回调验证：过期时间戳返回 403")
    void expiredTimestampReturns403() {
        WeComCallbackResult result = service.verifyUrl(
                "signature", "1000000000", "nonce", "echostr");
        assertEquals(403, result.getHttpStatus());
    }

    @Test
    @DisplayName("GET 回调验证：非法时间戳字符串返回 403")
    void invalidTimestampReturns403() {
        WeComCallbackResult result = service.verifyUrl(
                "signature", "not-a-number", "nonce", "echostr");
        assertEquals(403, result.getHttpStatus());
    }

    @Test
    @DisplayName("GET 回调验证：错误签名返回 403")
    void wrongSignatureReturns403() {
        WeComCallbackResult result = service.verifyUrl(
                "0000000000000000000000000000000000000000", "1409659813", "nonce", "some-encrypted-data");
        assertEquals(403, result.getHttpStatus());
    }

    @Test
    @DisplayName("GET 回调验证：密文被篡改返回 403")
    void tamperedCiphertextReturns403() {
        long timestamp = Instant.now(fixedClock).getEpochSecond();
        String ts = String.valueOf(timestamp);
        String nonce = "random-nonce";

        String encrypted = crypto.encryptContent("<xml>test</xml>", CORP_ID);
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";
        String signature = crypto.generateSignature(ts, nonce, tampered);

        WeComCallbackResult result = service.verifyUrl(signature, ts, nonce, tampered);
        assertEquals(403, result.getHttpStatus());
    }

    @Test
    @DisplayName("GET 回调验证：使用未来时间戳偏差超过 5 分钟返回 403")
    void futureTimestampTooFarReturns403() {
        long futureTs = Instant.now(fixedClock).getEpochSecond() + 600;
        WeComCallbackResult result = service.verifyUrl(
                "signature", String.valueOf(futureTs), "nonce", "data");
        assertEquals(403, result.getHttpStatus());
    }

    // ==================== POST 回调 - 参数校验 ====================

    @Test
    @DisplayName("POST 回调：缺少参数返回 400")
    void missingPostParamsReturns400() {
        assertEquals(400, service.receiveMessage("", "123", "nonce", "body").getHttpStatus());
        assertEquals(400, service.receiveMessage("sig", "", "nonce", "body").getHttpStatus());
        assertEquals(400, service.receiveMessage("sig", "123", "", "body").getHttpStatus());
        assertEquals(400, service.receiveMessage("sig", "123", "nonce", "").getHttpStatus());
    }

    @Test
    @DisplayName("POST 回调：请求体过大返回 413")
    void bodyTooLargeReturns413() {
        String ts = String.valueOf(fixedClock.instant().getEpochSecond());
        String largeBody = "x".repeat(65537);
        WeComCallbackResult result = service.receiveMessage("sig", ts, "nonce", largeBody);
        assertEquals(413, result.getHttpStatus());
    }

    @Test
    @DisplayName("POST 回调：时间戳偏差过大返回 403")
    void postTimestampDriftReturns403() {
        WeComCallbackResult result = service.receiveMessage("sig", "1000000000", "nonce", "<xml/>");
        assertEquals(403, result.getHttpStatus());
    }

    @Test
    @DisplayName("POST 回调：非法 XML 返回 400")
    void invalidXmlReturns400() {
        WeComCallbackResult result = service.receiveMessage("sig", String.valueOf(Instant.now(fixedClock).getEpochSecond()), "nonce", "not-xml");
        assertEquals(400, result.getHttpStatus());
    }

    // ==================== POST 回调 - 签名/解密/字段校验 ====================

    @Test
    @DisplayName("POST 回调：完整加密文本流程返回 200 success")
    void fullEncryptedCallbackReturns200() {
        when(conversationService.processInboundMessage(any())).thenReturn(
                InboundMessageResponse.builder()
                        .accepted(true).duplicate(false).messageId(1L)
                        .userId(1L).bindingId(1L).build());

        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        WeComCallbackResult result = sendEncryptedPost(innerXml);

        assertEquals(200, result.getHttpStatus());
        assertEquals("success", result.getBody());

        verify(conversationService).processInboundMessage(commandCaptor.capture());
        InboundMessageCommand cmd = commandCaptor.getValue();
        assertEquals("WECOM", cmd.getChannel());
        assertEquals("user001", cmd.getExternalUserId());
        assertEquals("12345", cmd.getExternalMessageId());
        assertEquals("你好", cmd.getText());
    }

    @Test
    @DisplayName("POST 回调：签名错误返回 403")
    void postInvalidSignatureReturns403() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
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

        WeComCallbackResult result = service.receiveMessage(wrongSignature, String.valueOf(timestamp), "nonce", outerXml);
        assertEquals(403, result.getHttpStatus());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("POST 回调：AgentID 不匹配返回 403")
    void agentIdMismatchReturns403() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>999999</AgentID>"
                + "</xml>";

        WeComCallbackResult result = sendEncryptedPost(innerXml);
        assertEquals(403, result.getHttpStatus());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("POST 回调：字段过长返回 400")
    void fieldTooLongReturns400() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + "x".repeat(65) + "]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        WeComCallbackResult result = sendEncryptedPost(innerXml);
        assertEquals(400, result.getHttpStatus());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("POST 回调：内容过长返回 400")
    void contentTooLongReturns400() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + "x".repeat(4001) + "]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        WeComCallbackResult result = sendEncryptedPost(innerXml);
        assertEquals(400, result.getHttpStatus());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("POST 回调：非文本消息类型返回 200 success 不调 service")
    void nonTextMsgTypeReturns200NoServiceCall() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[image]]></MsgType>"
                + "<Content><![CDATA[图片]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        WeComCallbackResult result = sendEncryptedPost(innerXml);
        assertEquals(200, result.getHttpStatus());
        assertEquals("success", result.getBody());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("POST 回调：CreateTime 非法返回 400")
    void invalidCreateTimeReturns400() {
        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>not-a-number</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        WeComCallbackResult result = sendEncryptedPost(innerXml);
        assertEquals(400, result.getHttpStatus());
        verifyNoInteractions(conversationService);
    }

    // ==================== 异步发送 ====================

    @Test
    @DisplayName("POST 回调：首次消息触发异步 AI 任务")
    void firstMessageTriggersAsyncAiTask() {
        when(conversationService.processInboundMessage(any())).thenReturn(
                InboundMessageResponse.builder()
                        .accepted(true).duplicate(false).messageId(1L)
                        .userId(1L).bindingId(1L).build());

        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        sendEncryptedPost(innerXml);

        verify(replyService, timeout(5000)).processAsync(eq(1L), eq(1L),
                eq("WECOM:12345"), eq("user001"), eq("你好"), eq(1L));
    }

    @Test
    @DisplayName("POST 回调：重复消息不触发异步任务")
    void duplicateMessageDoesNotTriggerAsyncTask() {
        when(conversationService.processInboundMessage(any())).thenReturn(
                InboundMessageResponse.builder()
                        .accepted(true).duplicate(true).messageId(1L).build());

        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        sendEncryptedPost(innerXml);

        verify(replyService, never()).processAsync(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST 回调：首次消息但无 userId 时不触发异步任务")
    void firstMessageWithoutUserIdDoesNotTriggerAsync() {
        when(conversationService.processInboundMessage(any())).thenReturn(
                InboundMessageResponse.builder()
                        .accepted(true).duplicate(false).messageId(1L).build());

        String innerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<FromUserName><![CDATA[user001]]></FromUserName>"
                + "<CreateTime>" + fixedClock.instant().getEpochSecond() + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[你好]]></Content>"
                + "<MsgId>12345</MsgId>"
                + "<AgentID>" + AGENT_ID + "</AgentID>"
                + "</xml>";

        sendEncryptedPost(innerXml);

        verify(replyService, never()).processAsync(any(), any(), any(), any(), any(), any());
    }

    // ==================== 辅助方法 ====================

    private WeComCallbackResult sendEncryptedPost(String innerXml) {
        String encrypted = crypto.encryptContent(innerXml, CORP_ID);
        long timestamp = fixedClock.instant().getEpochSecond();
        String nonce = "test-nonce";
        String signature = crypto.generateSignature(String.valueOf(timestamp), nonce, encrypted);

        String outerXml = "<xml>"
                + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
                + "<AgentID><![CDATA[" + AGENT_ID + "]]></AgentID>"
                + "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>"
                + "</xml>";

        return service.receiveMessage(signature, String.valueOf(timestamp), nonce, outerXml);
    }
}
