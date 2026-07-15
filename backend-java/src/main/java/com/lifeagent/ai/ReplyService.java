package com.lifeagent.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifeagent.common.Constants;
import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.ContextMessageItem;
import com.lifeagent.dto.turn.ContextPackage;
import com.lifeagent.dto.turn.TurnResolutionRequest;
import com.lifeagent.dto.turn.TurnResolutionResponse;
import com.lifeagent.entity.ChannelBindingEntity;
import com.lifeagent.entity.ConversationContextEntity;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationContextMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.service.ContextService;
import com.lifeagent.wecom.WeComApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.lifeagent.common.Constants.*;

/**
 * AI 回复服务：异步编排上下文读取、AI 调用、ASSISTANT 保存、渠道发送和缓存刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyService {

    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationContextMapper conversationContextMapper;
    private final ChannelBindingMapper channelBindingMapper;
    private final ContextService contextService;
    private final AiApiClient aiApiClient;
    private final WeComApiClient weComApiClient;
    private final AiProperties aiProperties;
    private final Clock clock;

    /**
     * 异步处理 AI 回复：读取上下文 -> 调用 AI -> 最终回复 -> 保存 -> 发送 -> 更新状态。
     * 本方法应在事务外执行，所有网络调用不持有数据库事务。
     */
    public void processAsync(Long userId, Long bindingId, String idempotencyKey,
                             String externalUserId, String currentMessage) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        log.info("AI 异步任务开始, userId={}, requestId={}", userId, requestId);

        // 1. 读取上下文
        ContextPackage context;
        try {
            context = contextService.getOrBuildContext(userId, currentMessage);
        } catch (Exception e) {
            log.error("读取上下文失败, userId={}, requestId={}", userId, requestId, e);
            sendFallbackAndExit(bindingId, idempotencyKey, externalUserId);
            return;
        }

        // 2. 调用 AI
        TurnResolutionRequest aiRequest = TurnResolutionRequest.builder()
                .requestId(requestId)
                .schemaVersion(SCHEMA_VERSION)
                .context(context)
                .build();

        TurnResolutionResponse aiResponse = aiApiClient.resolve(aiRequest);

        // 3. 确定最终回复
        FinalReplyResult finalResult = determineFinalReply(aiResponse);

        // 4. 保存 ASSISTANT(CREATED)
        Long assistantId;
        try {
            assistantId = saveAssistant(bindingId, idempotencyKey, finalResult.reply);
        } catch (DataIntegrityViolationException e) {
            log.warn("ASSISTANT 唯一约束冲突，跳过本次处理, idempotencyKey={}", idempotencyKey);
            return;
        } catch (Exception e) {
            log.error("保存 ASSISTANT 失败, idempotencyKey={}", idempotencyKey, e);
            return;
        }

        if (assistantId == null) {
            log.warn("ASSISTANT 未插入（可能重复）, idempotencyKey={}", idempotencyKey);
            return;
        }

        // 5. 更新上下文状态（摘要、Intent）
        if (!STATUS_AI_UNAVAILABLE.equals(aiResponse.getResolutionStatus())) {
            updateContextState(userId, aiResponse, context);
        }

        // 6. 发送并更新状态
        try {
            boolean sent = weComApiClient.sendTextMessage(externalUserId, finalResult.reply);
            LocalDateTime now = LocalDateTime.now(clock).withNano(0);
            if (sent) {
                conversationMessageMapper.updateDeliveryStatus(
                        assistantId, DeliveryStatus.SENT.name(), now);
                log.info("AI 回复发送成功, idempotencyKey={}, messageId={}", idempotencyKey, assistantId);
            } else {
                conversationMessageMapper.updateDeliveryStatus(
                        assistantId, DeliveryStatus.FAILED.name(), null);
                log.warn("AI 回复发送失败, idempotencyKey={}, messageId={}", idempotencyKey, assistantId);
            }
        } catch (Exception e) {
            log.error("AI 回复发送异常, idempotencyKey={}", idempotencyKey, e);
            conversationMessageMapper.updateDeliveryStatus(
                    assistantId, DeliveryStatus.FAILED.name(), null);
        }

        // 7. 刷新缓存
        try {
            contextService.evictCache(userId);
        } catch (Exception e) {
            log.warn("刷新缓存失败, userId={}", userId);
        }

        log.info("AI 异步任务完成, userId={}, requestId={}, intent={}, confidence={}",
                userId, requestId, aiResponse.getIntent(), aiResponse.getConfidence());
    }

    // ==================== 私有方法 ====================

    /**
     * 确定最终回复文案。
     */
    private FinalReplyResult determineFinalReply(TurnResolutionResponse response) {
        String status = response.getResolutionStatus();
        String intent = response.getIntent();
        String draft = response.getReplyDraft();

        // AI 不可用
        if (STATUS_AI_UNAVAILABLE.equals(status)) {
            return new FinalReplyResult(REPLY_AI_FALLBACK);
        }

        // 业务意图覆盖（不得使用 AI 草稿）
        if (INTENT_REMINDER_CREATE.equals(intent)
                || INTENT_HABIT_CREATE.equals(intent)
                || INTENT_PLAN_CREATE.equals(intent)
                || INTENT_ANALYSIS_REQUEST.equals(intent)) {
            return new FinalReplyResult(REPLY_FEATURE_IN_DEVELOPMENT);
        }

        // SMALL_TALK：使用 AI 草稿
        if (draft != null && !draft.isBlank()) {
            return new FinalReplyResult(draft);
        }

        return new FinalReplyResult(REPLY_AI_FALLBACK);
    }

    /**
     * 保存 ASSISTANT 消息。
     *
     * @return 消息 ID，唯一约束冲突时返回 null
     */
    private Long saveAssistant(Long bindingId, String idempotencyKey, String reply) {
        ConversationMessageEntity assistant = new ConversationMessageEntity();
        assistant.setChannelBindingId(bindingId);
        assistant.setIdempotencyKey(idempotencyKey);
        assistant.setRole(MessageRole.ASSISTANT.name());
        assistant.setContent(reply);
        assistant.setContentHash(hashContent(reply));
        assistant.setDeliveryStatus(DeliveryStatus.CREATED.name());

        try {
            // ASSISTANT 的 idempotency_key 与 USER 相同
            // 使用 ON CONFLICT DO NOTHING 保证唯一
            conversationMessageMapper.insertIgnoreAssistant(assistant);
            if (assistant.getId() != null && assistant.getId() > 0) {
                return assistant.getId();
            }
            return null;
        } catch (DataIntegrityViolationException e) {
            // 唯一约束冲突
            return null;
        }
    }

    /**
     * 更新上下文状态（摘要、Intent、置信度）。
     */
    private void updateContextState(Long userId, TurnResolutionResponse response, ContextPackage context) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 更新 Intents
        String intent = response.getIntent();
        double confidence = response.getConfidence();

        if (isValidIntent(intent)) {
            try {
                conversationContextMapper.updateLastResolution(
                        userId, intent, String.valueOf(confidence), null, now);
            } catch (Exception e) {
                log.warn("更新 Intent 状态失败, userId={}", userId);
            }
        }

        // 更新摘要
        boolean summaryRequested = context.isSummaryRequested();
        String updatedSummary = response.getUpdatedSummary();

        if (summaryRequested && updatedSummary != null && !updatedSummary.isBlank()
                && updatedSummary.length() <= aiProperties.getSummaryMaxChars()) {

            // 计算新的摘要截止 ID（summary_messages 最后一条的 ID）
            List<ContextMessageItem> summaryMessages = context.getSummaryMessages();
            Long newCutoffId = summaryMessages.isEmpty()
                    ? null : summaryMessages.get(summaryMessages.size() - 1).getMessageId();

            if (newCutoffId != null) {
                try {
                    conversationContextMapper.updateSummary(userId, updatedSummary, newCutoffId, now);
                    log.info("摘要更新成功, userId={}, cutoffId={}", userId, newCutoffId);
                } catch (Exception e) {
                    log.warn("摘要更新失败, userId={}", userId);
                }
            }
        }
    }

    /**
     * AI 上下文读取失败时的降级处理：保存固定回复并尝试发送。
     */
    private void sendFallbackAndExit(Long bindingId, String idempotencyKey, String externalUserId) {
        Long assistantId = saveAssistant(bindingId, idempotencyKey, REPLY_AI_FALLBACK);
        if (assistantId == null) {
            return;
        }
        try {
            boolean sent = weComApiClient.sendTextMessage(externalUserId, REPLY_AI_FALLBACK);
            if (sent) {
                conversationMessageMapper.updateDeliveryStatus(
                        assistantId, DeliveryStatus.SENT.name(),
                        LocalDateTime.now(clock).withNano(0));
            } else {
                conversationMessageMapper.updateDeliveryStatus(
                        assistantId, DeliveryStatus.FAILED.name(), null);
            }
        } catch (Exception e) {
            conversationMessageMapper.updateDeliveryStatus(
                    assistantId, DeliveryStatus.FAILED.name(), null);
        }
    }

    private boolean isValidIntent(String intent) {
        return INTENT_SMALL_TALK.equals(intent)
                || INTENT_REMINDER_CREATE.equals(intent)
                || INTENT_HABIT_CREATE.equals(intent)
                || INTENT_PLAN_CREATE.equals(intent)
                || INTENT_ANALYSIS_REQUEST.equals(intent);
    }

    private String hashContent(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(content.hashCode());
        }
    }

    @RequiredArgsConstructor
    private static class FinalReplyResult {
        final String reply;
    }
}
