package com.lifeagent.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeagent.cache.HabitDraftCache;
import com.lifeagent.cache.ReminderDraftCache;
import com.lifeagent.cache.model.HabitDraftCacheValue;
import com.lifeagent.cache.model.ReminderDraftCacheValue;
import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.*;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.entity.HabitEntity;
import com.lifeagent.entity.HabitExecutionEntity;
import com.lifeagent.entity.ReminderEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationContextMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.service.ContextService;
import com.lifeagent.service.HabitService;
import com.lifeagent.service.ReminderService;
import com.lifeagent.wecom.WeComApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
    private final ReminderService reminderService;
    private final ReminderDraftCache reminderDraftCache;
    private final HabitService habitService;
    private final HabitDraftCache habitDraftCache;
    private final ObjectMapper objectMapper;

    /**
     * 异步处理 AI 回复：读取上下文 -> 调用 AI -> 最终回复 -> 保存 -> 发送 -> 更新状态。
     * 本方法应在事务外执行，所有网络调用不持有数据库事务。
     */
    public void processAsync(Long userId, Long bindingId, String idempotencyKey,
                             String externalUserId, String currentMessage, Long sourceMessageId) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        log.info("AI 异步任务开始, userId={}, requestId={}", userId, requestId);

        // 1. 读取上下文（含提醒候选和最近 Reminder）
        ContextPackage context;
        try {
            context = buildContextWithReminderInfo(userId, currentMessage);
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
        String finalReply = determineFinalReply(aiResponse, userId, sourceMessageId,
                idempotencyKey, bindingId);

        if (finalReply == null) {
            log.warn("最终回复为空，跳过发送, requestId={}", requestId);
            return;
        }

        // 4. 保存 ASSISTANT(CREATED)
        Long assistantId;
        try {
            assistantId = saveAssistant(bindingId, idempotencyKey, finalReply);
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
            boolean sent = weComApiClient.sendTextMessage(externalUserId, finalReply);
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

    // ==================== 上下文构建（含提醒信息） ====================

    private ContextPackage buildContextWithReminderInfo(Long userId, String currentMessage) {
        ContextPackage base = contextService.getOrBuildContext(userId, currentMessage);

        // 添加 Java 时间基准
        OffsetDateTime now = OffsetDateTime.now(clock);
        String referenceTime = now.toString();

        // 查询 Redis 候选
        PendingReminderInfo pendingInfo = getPendingReminderInfo(userId);

        // 查询最近 Reminder
        ReminderEntity recentReminder = reminderService.findRecentReminder(userId);
        RecentReminderInfo recentInfo = null;
        if (recentReminder != null) {
            recentInfo = RecentReminderInfo.builder()
                    .reminderId(recentReminder.getId())
                    .version(recentReminder.getVersion())
                    .status(recentReminder.getStatus())
                    .content(recentReminder.getContent())
                    .eventAt(recentReminder.getEventAt())
                    .remindAt(null)
                    .lastSentAt(null)
                    .build();
        }

        // ========== LA-007 习惯上下文 ==========

        // 查询习惯 Redis 候选
        PendingHabitInfo pendingHabit = getPendingHabitInfo(userId);

        // 查询最近习惯
        List<RecentHabitInfo> recentHabits = getRecentHabitInfo(userId);

        // 查询最近执行
        RecentExecutionInfo recentExecution = getRecentExecutionInfo(userId);

        return ContextPackage.builder()
                .currentMessage(base.getCurrentMessage())
                .memorySummary(base.getMemorySummary())
                .recentMessages(base.getRecentMessages())
                .summaryRequested(base.isSummaryRequested())
                .summaryMessages(base.getSummaryMessages())
                .referenceTime(referenceTime)
                .timezone("Asia/Shanghai")
                .pendingReminder(pendingInfo)
                .recentReminder(recentInfo)
                .pendingHabit(pendingHabit)
                .recentHabits(recentHabits)
                .recentHabitExecution(recentExecution)
                .build();
    }

    // ==================== 最终回复决策 ====================

    /**
     * 确定最终回复文案，处理提醒业务逻辑。
     */
    private String determineFinalReply(TurnResolutionResponse response, Long userId,
                                        Long sourceMessageId, String idempotencyKey,
                                        Long bindingId) {
        String status = response.getResolutionStatus();
        String intent = response.getIntent();
        String draft = response.getReplyDraft();
        ReminderResolution reminderResolution = response.getReminderResolution();
        HabitResolution habitResolution = response.getHabitResolution();

        // AI 不可用
        if (STATUS_AI_UNAVAILABLE.equals(status)) {
            return REPLY_AI_FALLBACK;
        }

        // REMINDER_CREATE 意图：执行提醒操作
        if (INTENT_REMINDER_CREATE.equals(intent) && reminderResolution != null) {
            String action = reminderResolution.getAction();
            String timeSource = reminderResolution.getTimeSource();
            double confidence = response.getConfidence();

            // 低置信度不执行写操作
            if (confidence < aiProperties.getReminderWriteMinConfidence()) {
                // 返回候选追问
                if (draft != null && !draft.isBlank()) {
                    return draft;
                }
                return REPLY_AI_FALLBACK;
            }

            // UPSERT_DRAFT：保存或更新 Redis 候选
            if (DRAFT_ACTION_UPSERT_DRAFT.equals(action)) {
                return handleUpsertDraft(userId, idempotencyKey, reminderResolution, bindingId, draft);
            }

            // CONFIRM_DRAFT：确认候选
            if (DRAFT_ACTION_CONFIRM_DRAFT.equals(action)) {
                return handleConfirmDraft(userId, sourceMessageId, idempotencyKey, reminderResolution, draft);
            }

            // CREATE/MODIFY/ACK/COMPLETE/SNOOZE/CANCEL：执行写操作
            String reminderReply = reminderService.executeReminderAction(
                    userId, sourceMessageId, idempotencyKey, reminderResolution, clock);
            if (reminderReply != null) {
                return reminderReply;
            }

            // 回退到 draft
            if (draft != null && !draft.isBlank()) {
                return draft;
            }
            return REPLY_AI_FALLBACK;
        }

        // REMINDER_CREATE 意图但没有 reminder_resolution：仍按功能开发中处理
        if (INTENT_REMINDER_CREATE.equals(intent)) {
            // 但使用 AI draft 回复
            if (draft != null && !draft.isBlank()) {
                return draft;
            }
            return REPLY_FEATURE_IN_DEVELOPMENT;
        }

        // HABIT_CREATE 意图：执行习惯操作
        if (INTENT_HABIT_CREATE.equals(intent) && habitResolution != null) {
            String action = habitResolution.getAction();
            double confidence = response.getConfidence();

            if (confidence < aiProperties.getHabitWriteMinConfidence()) {
                if (draft != null && !draft.isBlank()) {
                    return draft;
                }
                return REPLY_AI_FALLBACK;
            }

            // UPSERT_DRAFT：保存或更新 Redis 候选
            if (HABIT_ACTION_UPSERT_DRAFT.equals(action)) {
                return handleHabitUpsertDraft(userId, idempotencyKey, habitResolution, draft);
            }

            // CONFIRM_DRAFT：确认候选
            if (HABIT_ACTION_CONFIRM_DRAFT.equals(action)) {
                return handleHabitConfirmDraft(userId, sourceMessageId, idempotencyKey, habitResolution, draft);
            }

            // ACK/COMPLETE/PAUSE/RESUME/CANCEL：执行写操作
            String habitReply = habitService.executeHabitAction(
                    userId, sourceMessageId, idempotencyKey, habitResolution, clock);
            if (habitReply != null) {
                return habitReply;
            }

            if (draft != null && !draft.isBlank()) {
                return draft;
            }
            return REPLY_AI_FALLBACK;
        }

        // HABIT_CREATE 意图但没有 habit_resolution：仍按功能开发中提示
        if (INTENT_HABIT_CREATE.equals(intent)) {
            if (draft != null && !draft.isBlank()) {
                return draft;
            }
            return REPLY_FEATURE_IN_DEVELOPMENT;
        }

        // PLAN_CREATE / ANALYSIS_REQUEST：功能开发中
        if (INTENT_PLAN_CREATE.equals(intent)
                || INTENT_ANALYSIS_REQUEST.equals(intent)) {
            return REPLY_FEATURE_IN_DEVELOPMENT;
        }

        // SMALL_TALK：使用 AI 草稿
        if (draft != null && !draft.isBlank()) {
            return draft;
        }

        return REPLY_AI_FALLBACK;
    }

    private PendingReminderInfo getPendingReminderInfo(Long userId) {
        ReminderDraftCacheValue draft = reminderDraftCache.get(userId);
        if (draft == null) {
            return null;
        }
        return PendingReminderInfo.builder()
                .draftToken(draft.getDraftToken())
                .content(draft.getContent())
                .eventAt(draft.getEventAt())
                .remindAt(draft.getRemindAt())
                .advanceRemindAt(draft.getAdvanceRemindAt())
                .timeSource(draft.getTimeSource())
                .draftStatus(draft.getDraftStatus())
                .targetReminderId(draft.getTargetReminderId())
                .targetReminderVersion(draft.getTargetReminderVersion())
                .build();
    }

    // ==================== 提醒候选操作 ====================

    private String handleUpsertDraft(Long userId, String idempotencyKey,
                                      ReminderResolution resolution, Long bindingId,
                                      String draft) {
        ReminderDraftCacheValue draftEntity = reminderDraftCache.get(userId);
        if (draftEntity == null) {
            // 新建候选
            draftEntity = new ReminderDraftCacheValue();
            draftEntity.setDraftToken(UUID.randomUUID().toString().replace("-", ""));
            draftEntity.setSourceMessageId(0L); // 候选阶段暂时无 sourceMessageId
            draftEntity.setContent(resolution.getContent());
            draftEntity.setEventAt(resolution.getEventAt());
            draftEntity.setRemindAt(resolution.getRemindAt());
            draftEntity.setAdvanceRemindAt(resolution.getAdvanceRemindAt());
            draftEntity.setTimezone("Asia/Shanghai");
            draftEntity.setTimeSource(resolution.getTimeSource());
            draftEntity.setDraftStatus("AWAITING_CONFIRMATION");
        } else {
            // 合并现有候选
            if (resolution.getContent() != null) {
                draftEntity.setContent(resolution.getContent());
            }
            if (resolution.getEventAt() != null) {
                draftEntity.setEventAt(resolution.getEventAt());
            }
            if (resolution.getRemindAt() != null) {
                draftEntity.setRemindAt(resolution.getRemindAt());
            }
            if (resolution.getAdvanceRemindAt() != null) {
                draftEntity.setAdvanceRemindAt(resolution.getAdvanceRemindAt());
            }
            if (resolution.getTimeSource() != null) {
                draftEntity.setTimeSource(resolution.getTimeSource());
            }
            draftEntity.setDraftStatus("AWAITING_CONFIRMATION");
        }

        boolean saved = reminderDraftCache.save(userId, draftEntity);
        if (!saved) {
            log.warn("Redis 候选保存失败，不声称已记录, userId={}", userId);
            return "暂时无法记录待确认提醒，请稍后完整重述。";
        }

        // 使用 AI draft 作为回复
        if (draft != null && !draft.isBlank()) {
            return draft;
        }
        return "已记下你的提醒信息，请在 30 分钟内确认。";
    }

    private String handleConfirmDraft(Long userId, Long sourceMessageId,
                                       String idempotencyKey,
                                       ReminderResolution resolution, String draft) {
        ReminderDraftCacheValue draftEntity = reminderDraftCache.get(userId);
        if (draftEntity == null || draftEntity.getRemindAt() == null) {
            log.warn("Redis 候选不存在或过期，无法确认, userId={}", userId);
            if (draft != null && !draft.isBlank()) {
                return draft;
            }
            return "待确认的提醒已过期，请重新说明。";
        }

        // 构建 CREATE 候选并执行
        ReminderResolution createResolution = ReminderResolution.builder()
                .target(DRAFT_TARGET_NEW)
                .action(DRAFT_ACTION_CREATE)
                .content(draftEntity.getContent())
                .eventAt(draftEntity.getEventAt())
                .remindAt(draftEntity.getRemindAt())
                .advanceRemindAt(draftEntity.getAdvanceRemindAt())
                .timeSource(draftEntity.getTimeSource())
                .build();

        String reply = reminderService.executeReminderAction(
                userId, sourceMessageId, idempotencyKey, createResolution, clock);
        if (reply != null) {
            // 删除 Redis 候选
            reminderDraftCache.deleteIfTokenMatches(userId, draftEntity.getDraftToken());
            return reply;
        }

        if (draft != null && !draft.isBlank()) {
            return draft;
        }
        return REPLY_AI_FALLBACK;
    }

    // ==================== 习惯上下文构建 ====================

    private PendingHabitInfo getPendingHabitInfo(Long userId) {
        HabitDraftCacheValue draft = habitDraftCache.get(userId);
        if (draft == null) {
            return null;
        }
        return PendingHabitInfo.builder()
                .draftToken(draft.getDraftToken())
                .name(draft.getName())
                .dailyTimes(draft.getDailyTimes())
                .startDate(draft.getStartDate())
                .endDate(draft.getEndDate())
                .timezone(draft.getTimezone())
                .draftStatus(draft.getDraftStatus())
                .build();
    }

    private List<RecentHabitInfo> getRecentHabitInfo(Long userId) {
        List<HabitEntity> habits = habitService.findRecentHabits(userId);
        return habits.stream()
                .map(h -> RecentHabitInfo.builder()
                        .habitId(h.getId())
                        .version(h.getVersion())
                        .name(h.getName())
                        .dailyTimes(parseDailyTimes(h.getDailyTimes()))
                        .status(h.getStatus())
                        .build())
                .toList();
    }

    private RecentExecutionInfo getRecentExecutionInfo(Long userId) {
        HabitExecutionEntity exec = habitService.findRecentExecution(userId);
        if (exec == null) {
            return null;
        }
        return RecentExecutionInfo.builder()
                .executionId(exec.getId())
                .habitId(exec.getHabitId())
                .occurrenceKey(exec.getOccurrenceKey())
                .status(exec.getStatus())
                .build();
    }

    private List<String> parseDailyTimes(String dailyTimesJson) {
        if (dailyTimesJson == null || dailyTimesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(dailyTimesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // ==================== 习惯候选操作 ====================

    private String handleHabitUpsertDraft(Long userId, String idempotencyKey,
                                           HabitResolution resolution, String replyDraft) {
        String reply = habitService.executeHabitAction(
                userId, 0L, idempotencyKey, resolution, clock);
        if (reply != null) {
            return reply;
        }

        if (replyDraft != null && !replyDraft.isBlank()) {
            return replyDraft;
        }
        return "已记下你的习惯信息，请在 30 分钟内确认。";
    }

    private String handleHabitConfirmDraft(Long userId, Long sourceMessageId,
                                            String idempotencyKey,
                                            HabitResolution resolution, String replyDraft) {
        HabitDraftCacheValue draft = habitDraftCache.get(userId);
        if (draft == null) {
            return "待确认的习惯已过期，请重新说明。";
        }

        String reply = habitService.executeHabitAction(
                userId, sourceMessageId, idempotencyKey, resolution, clock);
        if (reply != null) {
            return reply;
        }

        if (replyDraft != null && !replyDraft.isBlank()) {
            return replyDraft;
        }
        return "确认失败，请重试。";
    }

    // ==================== 私有方法（原 ReplyService） ====================

    /**
     * 保存 ASSISTANT 消息。
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
            conversationMessageMapper.insertIgnoreAssistant(assistant);
            if (assistant.getId() != null && assistant.getId() > 0) {
                return assistant.getId();
            }
            return null;
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    /**
     * 更新上下文状态（摘要、Intent、置信度）。
     */
    private void updateContextState(Long userId, TurnResolutionResponse response, ContextPackage context) {
        LocalDateTime now = LocalDateTime.now(clock);

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

        boolean summaryRequested = context.isSummaryRequested();
        String updatedSummary = response.getUpdatedSummary();

        if (summaryRequested && updatedSummary != null && !updatedSummary.isBlank()
                && updatedSummary.length() <= aiProperties.getSummaryMaxChars()) {

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
     * 降级处理：保存固定回复并尝试发送。
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
}
