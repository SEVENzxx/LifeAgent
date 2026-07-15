package com.lifeagent.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifeagent.common.Constants;
import com.lifeagent.config.WeComCrypto;
import com.lifeagent.config.WeComCrypto.DecryptResult;
import com.lifeagent.config.WeComProperties;
import com.lifeagent.config.WeComXmlParser;
import com.lifeagent.config.WeComXmlParser.CallbackXml;
import com.lifeagent.config.WeComXmlParser.TextMessageXml;
import com.lifeagent.dto.InboundMessageCommand;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.dto.WeComCallbackParams;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.service.ConversationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 企业微信回调业务服务。
 *
 * <p>承担验签、解密、XML 解析、字段校验等 WeCom 专属逻辑，
 * 输出渠道中立的 {@link InboundMessageCommand} 委托给
 * {@link ConversationService} 持久化。首次消息的事务提交后，
 * 通过 {@link TaskExecutor} 异步调用 {@link WeComApiClient} 发送固定回复。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "lifeagent.wecom.enabled", havingValue = "true")
public class WeComCallbackService {

    private final WeComProperties properties;
    private final ConversationService conversationService;
    private final ConversationMessageMapper conversationMessageMapper;
    private final WeComApiClient weComApiClient;
    private final Clock clock;

    @Resource(name = "applicationTaskExecutor")
    private TaskExecutor taskExecutor;

    private WeComCrypto weComCrypto;
    private WeComXmlParser xmlParser;

    @PostConstruct
    void initCrypto() {
        this.weComCrypto = new WeComCrypto(properties.getEncodingAesKey(), properties.getCallbackToken());
        this.xmlParser = new WeComXmlParser();
    }

    // ==================== GET 回调验证 ====================

    /**
     * 验证回调 URL 有效性。
     *
     * @param params  公共查询参数（msgSignature、timestamp、nonce）
     * @param echostr 加密的 echostr
     */
    public WeComCallbackResult verifyUrl(WeComCallbackParams params, String echostr) {
        return verifyUrl(params.msgSignature(), params.timestamp(), params.nonce(), echostr);
    }

    /**
     * 验证回调 URL 有效性（企业微信管理后台保存配置时调用）。
     */
    public WeComCallbackResult verifyUrl(String msgSignature, String timestamp, String nonce, String echostr) {
        if (isBlank(msgSignature) || isBlank(timestamp) || isBlank(nonce) || isBlank(echostr)) {
            log.warn("GET 回调缺少必填参数");
            return new WeComCallbackResult(400, "参数不完整");
        }

        if (!isTimestampWithinDrift(timestamp)) {
            log.warn("GET 回调时间戳偏差过大, timestamp={}", timestamp);
            return new WeComCallbackResult(403, "时间戳非法");
        }

        if (!weComCrypto.verifySignature(msgSignature, timestamp, nonce, echostr)) {
            log.warn("GET 回调签名验证失败");
            return new WeComCallbackResult(403, "签名验证失败");
        }

        DecryptResult decryptResult;
        try {
            decryptResult = weComCrypto.decryptAndParse(echostr);
        } catch (RuntimeException e) {
            log.warn("GET 回调解密失败: {}", e.getMessage());
            return new WeComCallbackResult(403, "解密失败");
        }

        if (!properties.getCorpId().equals(decryptResult.receiveId())) {
            log.warn("GET 回调 receiveId 不匹配, expected={}, actual={}",
                    properties.getCorpId(), decryptResult.receiveId());
            return new WeComCallbackResult(403, "企业信息不匹配");
        }

        log.info("GET 回调验证成功, timestamp={}, nonce={}", timestamp, nonce);
        return new WeComCallbackResult(200, decryptResult.content());
    }

    // ==================== POST 回调消息接收 ====================

    /**
     * 接收并处理企业微信推送的加密消息。
     *
     * @param params 公共查询参数（msgSignature、timestamp、nonce）
     * @param body   加密 XML 消息体
     */
    public WeComCallbackResult receiveMessage(WeComCallbackParams params, String body) {
        return receiveMessage(params.msgSignature(), params.timestamp(), params.nonce(), body);
    }

    /**
     * 接收并处理企业微信推送的加密消息。
     *
     * <p>验签、解密、解析文本消息后，以幂等方式持久化 USER + ASSISTANT(CREATED)。
     * 首次消息在事务提交后提交一次异步发送任务，返回 success。</p>
     */
    public WeComCallbackResult receiveMessage(String msgSignature, String timestamp, String nonce, String body) {
        if (isBlank(msgSignature) || isBlank(timestamp) || isBlank(nonce) || isBlank(body)) {
            log.warn("POST 回调缺少必填参数");
            return new WeComCallbackResult(400, "参数不完整");
        }

        if (body.length() > Constants.WECOM_MAX_BODY_SIZE) {
            log.warn("POST 回调请求体过大: {} bytes", body.length());
            return new WeComCallbackResult(413, "请求体过大");
        }

        if (!isTimestampWithinDrift(timestamp)) {
            log.warn("POST 回调时间戳偏差过大, timestamp={}", timestamp);
            return new WeComCallbackResult(403, "时间戳非法");
        }

        CallbackXml callbackXml;
        try {
            callbackXml = xmlParser.parseCallbackXml(body);
        } catch (IllegalArgumentException e) {
            log.warn("POST 回调外层 XML 解析失败");
            return new WeComCallbackResult(400, "XML 格式非法");
        }

        if (!weComCrypto.verifySignature(msgSignature, timestamp, nonce, callbackXml.encrypt())) {
            log.warn("POST 回调签名验证失败");
            return new WeComCallbackResult(403, "签名验证失败");
        }

        DecryptResult decryptResult;
        try {
            decryptResult = weComCrypto.decryptAndParse(callbackXml.encrypt());
        } catch (RuntimeException e) {
            log.warn("POST 回调解密失败");
            return new WeComCallbackResult(403, "解密失败");
        }

        if (!properties.getCorpId().equals(decryptResult.receiveId())) {
            log.warn("POST 回调 CorpID 不匹配");
            return new WeComCallbackResult(403, "企业信息不匹配");
        }

        TextMessageXml textMsg;
        try {
            textMsg = xmlParser.parseTextMessageXml(decryptResult.content());
        } catch (IllegalArgumentException e) {
            log.warn("POST 回调内容 XML 解析失败");
            return new WeComCallbackResult(400, "消息格式非法");
        }

        if (!"text".equals(textMsg.msgType())) {
            log.info("暂不支持的消息类型: {}, 返回 success", textMsg.msgType());
            return new WeComCallbackResult(200, "success");
        }

        if (isBlank(textMsg.msgId()) || isBlank(textMsg.fromUserName()) || isBlank(textMsg.content())) {
            log.warn("POST 回调文本消息必填字段缺失");
            return new WeComCallbackResult(400, "消息数据不完整");
        }

        if (textMsg.fromUserName().length() > 64 || textMsg.msgId().length() > 20) {
            log.warn("POST 回调字段长度超限, fromUserName={}, msgId={}",
                    textMsg.fromUserName(), textMsg.msgId());
            return new WeComCallbackResult(400, "消息数据格式非法");
        }

        if (textMsg.content().length() > 4000) {
            log.warn("POST 回调消息内容过长");
            return new WeComCallbackResult(400, "消息内容过长");
        }

        if (!properties.getAgentId().equals(textMsg.agentId())) {
            log.warn("POST 回调 AgentID 不匹配, expected={}, actual={}",
                    properties.getAgentId(), textMsg.agentId());
            return new WeComCallbackResult(403, "应用不匹配");
        }

        OffsetDateTime sentAt;
        try {
            long epochSecond = Long.parseLong(textMsg.createTime());
            sentAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
        } catch (NumberFormatException | ArithmeticException e) {
            log.warn("POST 回调 CreateTime 格式非法: {}", textMsg.createTime());
            return new WeComCallbackResult(400, "消息时间非法");
        }

        InboundMessageCommand command = InboundMessageCommand.builder()
                .channel(Constants.CHANNEL_WECOM)
                .externalUserId(textMsg.fromUserName())
                .externalMessageId(textMsg.msgId())
                .text(textMsg.content())
                .sentAt(sentAt)
                .build();

        try {
            InboundMessageResponse response = conversationService.processInboundMessage(command);
            if (!response.isDuplicate()) {
                String idempotencyKey = Constants.CHANNEL_WECOM + ":" + textMsg.msgId();
                triggerAsyncSend(textMsg.fromUserName(), idempotencyKey);
            }
        } catch (DataIntegrityViolationException e) {
            log.error("POST 回调数据库完整性错误, msgId={}", textMsg.msgId(), e);
            return new WeComCallbackResult(503, "服务暂时不可用");
        } catch (RuntimeException e) {
            log.error("POST 回调处理失败, msgId={}", textMsg.msgId(), e);
            return new WeComCallbackResult(503, "服务暂时不可用");
        }

        log.info("POST 回调处理成功, msgId={}", textMsg.msgId());
        return new WeComCallbackResult(200, "success");
    }

    /**
     * 提交一次进程内异步发送任务。
     * <p>发送成功更新 ASSISTANT 为 SENT + sent_at，失败更新为 FAILED。</p>
     */
    private void triggerAsyncSend(String externalUserId, String idempotencyKey) {
        taskExecutor.execute(() -> {
            try {
                ConversationMessageEntity assistant = conversationMessageMapper.selectOne(
                        new LambdaQueryWrapper<ConversationMessageEntity>()
                                .eq(ConversationMessageEntity::getIdempotencyKey, idempotencyKey)
                                .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT.name()));
                if (assistant == null) {
                    log.warn("异步发送未找到 ASSISTANT 消息, idempotencyKey={}", idempotencyKey);
                    return;
                }
                boolean sent = weComApiClient.sendTextMessage(externalUserId, assistant.getContent());
                if (sent) {
                    conversationMessageMapper.updateDeliveryStatus(
                            assistant.getId(), DeliveryStatus.SENT.name(),
                            LocalDateTime.now(clock).withNano(0));
                    log.info("异步发送成功, idempotencyKey={}, messageId={}", idempotencyKey, assistant.getId());
                } else {
                    conversationMessageMapper.updateDeliveryStatus(
                            assistant.getId(), DeliveryStatus.FAILED.name(), null);
                    log.info("异步发送失败, idempotencyKey={}, messageId={}", idempotencyKey, assistant.getId());
                }
            } catch (Exception e) {
                log.error("异步发送异常, idempotencyKey={}", idempotencyKey, e);
            }
        });
    }

    // ==================== 私有工具方法 ====================

    private boolean isTimestampWithinDrift(String timestampStr) {
        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = Instant.now(clock).getEpochSecond();
            return Math.abs(now - timestamp) <= Constants.WECOM_MAX_TIMESTAMP_DRIFT_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
