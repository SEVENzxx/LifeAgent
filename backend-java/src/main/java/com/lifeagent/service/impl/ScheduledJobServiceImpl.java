package com.lifeagent.service.impl;

import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.ReminderResolution;
import com.lifeagent.entity.ChannelBindingEntity;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.entity.HabitEntity;
import com.lifeagent.entity.HabitExecutionEntity;
import com.lifeagent.entity.ReminderEntity;
import com.lifeagent.entity.ScheduledJobEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.ExecutionStatus;
import com.lifeagent.enums.HabitStatus;
import com.lifeagent.enums.JobType;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.enums.ReminderStatus;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.mapper.HabitExecutionMapper;
import com.lifeagent.mapper.HabitMapper;
import com.lifeagent.mapper.ReminderMapper;
import com.lifeagent.mapper.ScheduledJobMapper;
import com.lifeagent.service.HabitService;
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
    private final HabitService habitService;
    private final HabitMapper habitMapper;
    private final HabitExecutionMapper habitExecutionMapper;
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
        String jobType = job.getJobType();

        if (JobType.REMINDER_DELIVERY.name().equals(jobType)) {
            executeReminderJob(job, now);
        } else if (JobType.HABIT_DELIVERY.name().equals(jobType)) {
            executeHabitJob(job, now);
        } else {
            log.warn("未知 Job 类型, jobId={}, jobType={}", job.getId(), jobType);
        }
    }

    /**
     * 执行 REMINDER_DELIVERY Job。
     */
    private void executeReminderJob(ScheduledJobEntity job, Instant now) {
        Long reminderId = job.getReminderId();

        ReminderEntity reminder = reminderMapper.selectById(reminderId);
        if (reminder == null) {
            log.warn("Job 对应的 Reminder 不存在, jobId={}, reminderId={}", job.getId(), reminderId);
            scheduledJobMapper.markFailed(job.getId(), "REMINDER_NOT_FOUND", now);
            return;
        }

        String rStatus = reminder.getStatus();
        if (!ReminderStatus.ACTIVE.name().equals(rStatus)
                && !ReminderStatus.ACKNOWLEDGED.name().equals(rStatus)) {
            log.info("Reminder 状态不允许发送, jobId={}, reminderStatus={}", job.getId(), rStatus);
            scheduledJobMapper.cancelByReminder(reminderId, now);
            return;
        }

        Long userId = reminder.getUserId();
        ChannelBindingEntity binding = findUserBinding(userId);
        if (binding == null) {
            log.warn("用户没有有效的渠道绑定, userId={}, jobId={}", userId, job.getId());
            scheduledJobMapper.markFailed(job.getId(), "NO_BINDING", now);
            return;
        }

        String assistantIdempotencyKey = "REMINDER_JOB:" + job.getId();
        String deliveryContent = reminderService.buildDeliveryMessage(reminder.getContent());

        Long assistantId = createOrFindAssistant(binding.getId(), assistantIdempotencyKey, deliveryContent, job);
        if (assistantId == null) {
            return;
        }

        boolean sent = sendDelivery(binding.getExternalUserId(), deliveryContent, job);
        if (sent) {
            handleReminderSuccess(job, reminder, assistantId, now);
        } else {
            handleReminderFailure(job, reminder, assistantId, now);
        }
    }

    /**
     * 执行 HABIT_DELIVERY Job。
     */
    private void executeHabitJob(ScheduledJobEntity job, Instant now) {
        Long habitExecutionId = job.getHabitExecutionId();
        if (habitExecutionId == null) {
            log.warn("HABIT_DELIVERY 缺少 habit_execution_id, jobId={}", job.getId());
            scheduledJobMapper.markFailed(job.getId(), "NO_EXECUTION_ID", now);
            return;
        }

        HabitExecutionEntity execution = habitExecutionMapper.selectById(habitExecutionId);
        if (execution == null) {
            log.warn("Job 对应的 Execution 不存在, jobId={}, habitExecutionId={}", job.getId(), habitExecutionId);
            scheduledJobMapper.markFailed(job.getId(), "EXECUTION_NOT_FOUND", now);
            return;
        }

        // 检查 Execution 状态
        if (!ExecutionStatus.SCHEDULED.name().equals(execution.getStatus())) {
            log.info("Execution 状态不允许发送, jobId={}, executionStatus={}", job.getId(), execution.getStatus());
            scheduledJobMapper.markFailed(job.getId(), "INVALID_EXECUTION_STATUS", now);
            return;
        }

        HabitEntity habit = habitMapper.selectById(execution.getHabitId());
        if (habit == null) {
            log.warn("Execution 对应的 Habit 不存在, executionId={}, habitId={}",
                    execution.getId(), execution.getHabitId());
            scheduledJobMapper.markFailed(job.getId(), "HABIT_NOT_FOUND", now);
            return;
        }

        if (!HabitStatus.ACTIVE.name().equals(habit.getStatus())) {
            log.info("Habit 不是 ACTIVE 状态, jobId={}, habitStatus={}", job.getId(), habit.getStatus());
            scheduledJobMapper.cancelByHabit(habit.getId(), now);
            return;
        }

        Long userId = habit.getUserId();
        ChannelBindingEntity binding = findUserBinding(userId);
        if (binding == null) {
            log.warn("用户没有有效的渠道绑定, userId={}, jobId={}", userId, job.getId());
            scheduledJobMapper.markFailed(job.getId(), "NO_BINDING", now);
            return;
        }

        String assistantIdempotencyKey = "HABIT_JOB:" + job.getId();
        String deliveryContent = habitService.buildDeliveryMessage(habit.getName());

        Long assistantId = createOrFindAssistant(binding.getId(), assistantIdempotencyKey, deliveryContent, job);
        if (assistantId == null) {
            return;
        }

        boolean sent = sendDelivery(binding.getExternalUserId(), deliveryContent, job);
        if (sent) {
            handleHabitSuccess(job, execution, assistantId, now);
        } else {
            handleHabitFailure(job, execution, assistantId, now);
        }
    }

    /**
     * 查找用户的企业微信渠道绑定。
     */
    private ChannelBindingEntity findUserBinding(Long userId) {
        return channelBindingMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelBindingEntity>()
                        .eq(ChannelBindingEntity::getUserId, userId)
                        .eq(ChannelBindingEntity::getChannel, CHANNEL_WECOM)
                        .last("LIMIT 1"));
    }

    /**
     * 创建或查找幂等 ASSISTANT 消息。
     */
    private Long createOrFindAssistant(Long bindingId, String idempotencyKey,
                                        String content, ScheduledJobEntity job) {
        Long assistantId = createAssistantMessage(bindingId, idempotencyKey, content);
        if (assistantId == null) {
            log.warn("ASSISTANT 已存在（幂等跳过）, idempotencyKey={}, jobId={}", idempotencyKey, job.getId());
            assistantId = findAssistantIdByKey(idempotencyKey);
        }
        if (assistantId == null) {
            log.warn("无法获取 ASSISTANT 消息 ID, jobId={}", job.getId());
        }
        return assistantId;
    }

    /**
     * 发送消息到企业微信。
     */
    private boolean sendDelivery(String externalUserId, String content, ScheduledJobEntity job) {
        try {
            return weComApiClient.sendTextMessage(externalUserId, content);
        } catch (Exception e) {
            log.warn("企业微信发送异常, jobId={}", job.getId(), e);
            return false;
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

    // ==================== 提醒成功处理 ====================

    private void handleReminderSuccess(ScheduledJobEntity job, ReminderEntity reminder,
                                        Long assistantId, Instant now) {
        scheduledJobMapper.markSucceeded(job.getId(), assistantId, now);

        conversationMessageMapper.updateDeliveryStatus(
                assistantId, DeliveryStatus.SENT.name(),
                LocalDateTime.ofInstant(now, ZoneId.systemDefault()).withNano(0));

        List<ScheduledJobEntity> remainingJobs = scheduledJobMapper.selectByReminderAndStatus(
                reminder.getId(), "READY");
        List<ScheduledJobEntity> remainingRetry = scheduledJobMapper.selectByReminderAndStatus(
                reminder.getId(), "RETRY_WAIT");

        if (remainingJobs.isEmpty() && remainingRetry.isEmpty()) {
            reminderMapper.updateStatusWithVersion(
                    reminder.getId(), ReminderStatus.DELIVERED.name(),
                    reminder.getStatus(), reminder.getVersion(), now);
            log.info("提醒所有节点已发送, reminderId={}", reminder.getId());
        }

        log.info("Job 发送成功, jobId={}, assistantId={}", job.getId(), assistantId);
    }

    // ==================== 提醒失败处理 ====================

    private void handleReminderFailure(ScheduledJobEntity job, ReminderEntity reminder,
                                        Long assistantId, Instant now) {
        int retryCount = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int maxRetries = aiProperties.getReminderMaxRetries();

        if (retryCount < maxRetries) {
            long[] delays = parseRetryDelays();
            long delayMinutes = retryCount < delays.length
                    ? delays[retryCount] : delays[delays.length - 1];
            Instant nextRun = now.plusSeconds(delayMinutes * 60);

            scheduledJobMapper.markRetryWait(job.getId(), nextRun, "SEND_FAILED", now);
            log.info("Job 发送失败，将重试, jobId={}, retryCount={}, nextRun={}",
                    job.getId(), retryCount + 1, nextRun);
        } else {
            scheduledJobMapper.markFailed(job.getId(), "RETRY_EXHAUSTED", now);
            conversationMessageMapper.updateDeliveryStatus(
                    assistantId, DeliveryStatus.FAILED.name(), null);
            log.warn("Job 重试耗尽, jobId={}, reminderId={}", job.getId(), reminder.getId());

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

    // ==================== 习惯成功处理 ====================

    private void handleHabitSuccess(ScheduledJobEntity job, HabitExecutionEntity execution,
                                     Long assistantId, Instant now) {
        scheduledJobMapper.markSucceeded(job.getId(), assistantId, now);

        conversationMessageMapper.updateDeliveryStatus(
                assistantId, DeliveryStatus.SENT.name(),
                LocalDateTime.ofInstant(now, ZoneId.systemDefault()).withNano(0));

        // 更新 Execution 为 DELIVERED
        habitExecutionMapper.updateStatusWithVersion(
                execution.getId(), ExecutionStatus.DELIVERED.name(),
                execution.getStatus(), execution.getVersion(), now);

        log.info("习惯 Job 发送成功, jobId={}, executionId={}", job.getId(), execution.getId());
    }

    // ==================== 习惯失败处理 ====================

    private void handleHabitFailure(ScheduledJobEntity job, HabitExecutionEntity execution,
                                     Long assistantId, Instant now) {
        int retryCount = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int maxRetries = aiProperties.getReminderMaxRetries();

        if (retryCount < maxRetries) {
            long[] delays = parseRetryDelays();
            long delayMinutes = retryCount < delays.length
                    ? delays[retryCount] : delays[delays.length - 1];
            Instant nextRun = now.plusSeconds(delayMinutes * 60);

            scheduledJobMapper.markRetryWait(job.getId(), nextRun, "SEND_FAILED", now);
            log.info("习惯 Job 发送失败，将重试, jobId={}, retryCount={}, nextRun={}",
                    job.getId(), retryCount + 1, nextRun);
        } else {
            scheduledJobMapper.markFailed(job.getId(), "RETRY_EXHAUSTED", now);
            conversationMessageMapper.updateDeliveryStatus(
                    assistantId, DeliveryStatus.FAILED.name(), null);

            // 更新 Execution 为 FAILED（不影响 Habit 后续调度）
            habitExecutionMapper.updateStatusWithVersion(
                    execution.getId(), ExecutionStatus.FAILED.name(),
                    execution.getStatus(), execution.getVersion(), now);

            log.warn("习惯 Job 重试耗尽, jobId={}, executionId={}", job.getId(), execution.getId());
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

    private Long findAssistantIdByKey(String idempotencyKey) {
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
