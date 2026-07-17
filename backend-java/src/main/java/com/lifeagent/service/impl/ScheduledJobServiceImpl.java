package com.lifeagent.service.impl;

import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.ReminderResolution;
import com.lifeagent.entity.ChannelBindingEntity;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.entity.ReminderEntity;
import com.lifeagent.entity.ScheduledJobEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.enums.ReminderStatus;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.mapper.ReminderMapper;
import com.lifeagent.mapper.ScheduledJobMapper;
import com.lifeagent.service.ReminderService;
import com.lifeagent.service.ScheduledJobService;
import com.lifeagent.wecom.WeComApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import static com.lifeagent.common.Constants.*;

/**
 * 调度任务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobServiceImpl implements ScheduledJobService {

    private final ScheduledJobMapper scheduledJobMapper;
    private final ReminderMapper reminderMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ChannelBindingMapper channelBindingMapper;
    private final ReminderService reminderService;
    private final WeComApiClient weComApiClient;
    private final AiProperties aiProperties;
    private final Clock clock;

    @Override
    public void scanAndExecute() {
        String owner = generateOwnerId();
        Instant now = Instant.now(clock);
        int batchSize = aiProperties.getReminderJobBatchSize();

        // 1. 标记超过宽限的 Job 为 MISSED
        Instant graceDeadline = now.minusSeconds(aiProperties.getReminderLateGraceMinutes() * 60);
        scheduledJobMapper.markMissed(graceDeadline, now);

        // 2. 扫描可领取的 Job
        List<ScheduledJobEntity> dueJobs;
        try {
            dueJobs = scheduledJobMapper.scanDueJobs(now, batchSize);
        } catch (Exception e) {
            log.warn("扫描到期 Job 失败", e);
            return;
        }

        if (dueJobs.isEmpty()) {
            return;
        }

        // 3. 领取每个 Job
        for (ScheduledJobEntity job : dueJobs) {
            try {
                Instant leaseUntil = now.plusSeconds(aiProperties.getReminderJobLeaseSeconds());
                int acquired = scheduledJobMapper.acquireLease(
                        job.getId(), owner, leaseUntil, now);
                if (acquired == 0) {
                    continue; // 已被其他实例领取
                }
                executeJob(job);
            } catch (Exception e) {
                log.error("Job 执行异常, jobId={}", job.getId(), e);
            }
        }
    }

    // ==================== 执行单个 Job ====================

    private void executeJob(ScheduledJobEntity job) {
        Instant now = Instant.now(clock);
        Long reminderId = job.getReminderId();

        // 1. 重新读取 Reminder
        ReminderEntity reminder = reminderMapper.selectById(reminderId);
        if (reminder == null) {
            log.warn("Job 对应的 Reminder 不存在, jobId={}, reminderId={}", job.getId(), reminderId);
            scheduledJobMapper.markFailed(job.getId(), "REMINDER_NOT_FOUND", now);
            return;
        }

        // 2. 检查 Reminder 状态
        String rStatus = reminder.getStatus();
        if (!ReminderStatus.ACTIVE.name().equals(rStatus)
                && !ReminderStatus.ACKNOWLEDGED.name().equals(rStatus)) {
            log.info("Reminder 状态不允许发送, jobId={}, reminderStatus={}", job.getId(), rStatus);
            scheduledJobMapper.cancelByReminder(reminderId, now);
            return;
        }

        // 3. 查找用户的渠道绑定
        Long userId = reminder.getUserId();
        ChannelBindingEntity binding = channelBindingMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelBindingEntity>()
                        .eq(ChannelBindingEntity::getUserId, userId)
                        .eq(ChannelBindingEntity::getChannel, CHANNEL_WECOM)
                        .last("LIMIT 1"));
        if (binding == null) {
            log.warn("用户没有有效的渠道绑定, userId={}, jobId={}", userId, job.getId());
            scheduledJobMapper.markFailed(job.getId(), "NO_BINDING", now);
            return;
        }

        // 4. 生成幂等 ASSISTANT 消息
        String assistantIdempotencyKey = "REMINDER_JOB:" + job.getId();
        String deliveryContent = reminderService.buildDeliveryMessage(reminder.getContent());

        // 5. 创建 ASSISTANT 消息（事务内）
        Long assistantId = createAssistantMessage(binding.getId(), assistantIdempotencyKey, deliveryContent);
        if (assistantId == null) {
            log.warn("ASSISTANT 已存在（幂等跳过）, idempotencyKey={}, jobId={}",
                    assistantIdempotencyKey, job.getId());
            // 尝试查找已有 ASSISTANT
            assistantId = findAssistantId(job.getId());
        }

        if (assistantId == null) {
            log.warn("无法获取 ASSISTANT 消息 ID, jobId={}", job.getId());
            return;
        }

        // 6. 发送（事务外）
        boolean sent;
        try {
            sent = weComApiClient.sendTextMessage(binding.getExternalUserId(), deliveryContent);
        } catch (Exception e) {
            log.warn("企业微信发送异常, jobId={}", job.getId(), e);
            sent = false;
        }

        // 7. 更新状态
        if (sent) {
            handleSuccess(job, reminder, assistantId, now);
        } else {
            handleFailure(job, reminder, assistantId, now);
        }
    }

    @Transactional
    Long createAssistantMessage(Long bindingId, String idempotencyKey, String content) {
        ConversationMessageEntity assistant = new ConversationMessageEntity();
        assistant.setChannelBindingId(bindingId);
        assistant.setIdempotencyKey(idempotencyKey);
        assistant.setRole(MessageRole.ASSISTANT.name());
        assistant.setContent(content);
        assistant.setContentHash(hashContent(content));
        assistant.setDeliveryStatus(DeliveryStatus.CREATED.name());
        return conversationMessageMapper.insertIgnoreAssistant(assistant) > 0 ? assistant.getId() : null;
    }

    // ==================== 成功处理 ====================

    private void handleSuccess(ScheduledJobEntity job, ReminderEntity reminder,
                                Long assistantId, Instant now) {
        // 1. 更新 Job 为 SUCCEEDED
        scheduledJobMapper.markSucceeded(job.getId(), assistantId, now);

        // 2. 更新 ASSISTANT 为 SENT
        conversationMessageMapper.updateDeliveryStatus(
                assistantId, DeliveryStatus.SENT.name(),
                LocalDateTime.ofInstant(now, ZoneId.systemDefault()).withNano(0));

        // 3. 检查是否有其他未来 Job
        List<ScheduledJobEntity> remainingJobs = scheduledJobMapper.selectByReminderAndStatus(
                reminder.getId(), "READY");
        List<ScheduledJobEntity> remainingRetry = scheduledJobMapper.selectByReminderAndStatus(
                reminder.getId(), "RETRY_WAIT");

        if (remainingJobs.isEmpty() && remainingRetry.isEmpty()) {
            // 没有剩余节点，标记 Reminder 为 DELIVERED
            reminderMapper.updateStatusWithVersion(
                    reminder.getId(), ReminderStatus.DELIVERED.name(),
                    reminder.getStatus(), reminder.getVersion(), now);
            log.info("提醒所有节点已发送, reminderId={}", reminder.getId());
        }

        log.info("Job 发送成功, jobId={}, assistantId={}", job.getId(), assistantId);
    }

    // ==================== 失败处理 ====================

    private void handleFailure(ScheduledJobEntity job, ReminderEntity reminder,
                                Long assistantId, Instant now) {
        int retryCount = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int maxRetries = aiProperties.getReminderMaxRetries();

        if (retryCount < maxRetries) {
            // 计算下次重试时间
            long[] delays = parseRetryDelays();
            long delayMinutes = retryCount < delays.length
                    ? delays[retryCount] : delays[delays.length - 1];
            Instant nextRun = now.plusSeconds(delayMinutes * 60);

            scheduledJobMapper.markRetryWait(job.getId(), nextRun, "SEND_FAILED", now);
            log.info("Job 发送失败，将重试, jobId={}, retryCount={}, nextRun={}",
                    job.getId(), retryCount + 1, nextRun);
        } else {
            // 重试耗尽
            scheduledJobMapper.markFailed(job.getId(), "RETRY_EXHAUSTED", now);
            conversationMessageMapper.updateDeliveryStatus(
                    assistantId, DeliveryStatus.FAILED.name(), null);
            log.warn("Job 重试耗尽, jobId={}, reminderId={}", job.getId(), reminder.getId());

            // 检查是否还有其他 Job
            List<ScheduledJobEntity> remainingJobs = scheduledJobMapper.selectByReminderAndStatus(
                    reminder.getId(), "READY");
            List<ScheduledJobEntity> remainingRetry = scheduledJobMapper.selectByReminderAndStatus(
                    reminder.getId(), "RETRY_WAIT");
            if (remainingJobs.isEmpty() && remainingRetry.isEmpty()) {
                reminderMapper.updateStatusWithVersion(
                        reminder.getId(), ReminderStatus.FAILED.name(),
                        reminder.getStatus(), reminder.getVersion(), now);
                log.warn("提醒最终失败（无其他可执行节点）, reminderId={}", reminder.getId());
            }
        }
    }

    // ==================== 工具 ====================

    private long[] parseRetryDelays() {
        String raw = aiProperties.getReminderRetryDelaysMinutes();
        try {
            String[] parts = raw.split(",");
            long[] delays = new long[parts.length];
            for (int i = 0; i < parts.length; i++) {
                delays[i] = Long.parseLong(parts[i].replace("m", "").trim());
            }
            return delays;
        } catch (Exception e) {
            return new long[]{1, 5, 15};
        }
    }

    private String generateOwnerId() {
        return APPLICATION_NAME + "@" + System.getenv("HOSTNAME");
    }

    private Long findAssistantId(Long jobId) {
        String idempotencyKey = "REMINDER_JOB:" + jobId;
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationMessageEntity>()
                .eq(ConversationMessageEntity::getIdempotencyKey, idempotencyKey)
                .orderByAsc(ConversationMessageEntity::getId)
                .last("LIMIT 1");
        ConversationMessageEntity existing = conversationMessageMapper.selectOne(wrapper);
        return existing != null ? existing.getId() : null;
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(content.hashCode());
        }
    }
}
