package com.lifeagent.service.impl;

import com.lifeagent.common.Constants;
import com.lifeagent.dto.turn.ReminderResolution;
import com.lifeagent.entity.ReminderEntity;
import com.lifeagent.entity.ScheduledJobEntity;
import com.lifeagent.enums.JobType;
import com.lifeagent.enums.NodeType;
import com.lifeagent.enums.ReminderStatus;
import com.lifeagent.mapper.ReminderMapper;
import com.lifeagent.mapper.ScheduledJobMapper;
import com.lifeagent.service.ReminderService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static com.lifeagent.common.Constants.*;

/**
 * 提醒业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderMapper reminderMapper;
    private final ScheduledJobMapper scheduledJobMapper;

    @Override
    public String executeReminderAction(Long userId, Long sourceMessageId,
                                         String idempotencyKey,
                                         ReminderResolution resolution, Clock clock) {
        String action = resolution.getAction();
        Instant now = Instant.now(clock);
        String userTimezone = "Asia/Shanghai"; // P0 固定值

        return switch (action) {
            case DRAFT_ACTION_CREATE -> handleCreate(userId, sourceMessageId, resolution, now, userTimezone);
            case DRAFT_ACTION_MODIFY -> handleModify(userId, resolution, now);
            case DRAFT_ACTION_ACK -> handleAck(userId, resolution, now);
            case DRAFT_ACTION_COMPLETE -> handleComplete(userId, resolution, now);
            case DRAFT_ACTION_SNOOZE -> handleSnooze(userId, resolution, now);
            case DRAFT_ACTION_CANCEL -> handleCancel(userId, resolution, now);
            default -> null;
        };
    }

    @Override
    public ReminderEntity findRecentReminder(Long userId) {
        return reminderMapper.selectRecentByUserId(userId);
    }

    @Override
    public String buildDeliveryMessage(String content) {
        return REMINDER_MESSAGE_PREFIX + content;
    }

    // ==================== 创建 ====================

    @Transactional
    String handleCreate(Long userId, Long sourceMessageId, ReminderResolution resolution,
                        Instant now, String timezone) {
        String content = resolution.getContent();
        Instant remindAt = resolution.getRemindAt();

        if (content == null || remindAt == null) {
            log.warn("提醒创建缺少必填字段, userId={}", userId);
            return null;
        }

        if (remindAt.isBefore(now)) {
            log.warn("提醒时间已过去, remindAt={}", remindAt);
            return "提醒时间已过去，请重新设置。";
        }

        // 创建 Reminder
        ReminderEntity reminder = new ReminderEntity();
        reminder.setUserId(userId);
        reminder.setContent(content);
        reminder.setEventAt(resolution.getEventAt());
        reminder.setTimezone(timezone);
        reminder.setStatus(ReminderStatus.ACTIVE.name());
        reminder.setSourceMessageId(sourceMessageId);
        reminder.setVersion(1);
        reminder.setCreatedAt(now);
        reminder.setUpdatedAt(now);

        int rows = reminderMapper.insertIgnore(reminder);
        if (rows == 0) {
            log.warn("提醒已存在（幂等跳过）, userId={}", userId);
            return "该提醒已经设置过了。";
        }

        // 创建主节点 Job
        String primaryBizKey = "REMINDER_DELIVERY:" + reminder.getId() + ":PRIMARY";
        createJob(reminder.getId(), NodeType.PRIMARY.name(), primaryBizKey, remindAt, remindAt, now);

        // 可选提前节点
        Instant advanceAt = resolution.getAdvanceRemindAt();
        if (advanceAt != null && advanceAt.isAfter(now) && advanceAt.isBefore(remindAt)) {
            String advanceBizKey = "REMINDER_DELIVERY:" + reminder.getId() + ":ADVANCE";
            createJob(reminder.getId(), NodeType.ADVANCE.name(), advanceBizKey, advanceAt, advanceAt, now);
        }

        log.info("提醒创建成功, reminderId={}", reminder.getId());
        return String.format("已设置，我会在 %s 提醒你%s。",
                formatInstant(remindAt, timezone),
                content);
    }

    // ==================== 修改 ====================

    @Transactional
    String handleModify(Long userId, ReminderResolution resolution, Instant now) {
        // 修改必须基于已有的 Reminder，由调用方传入 resolution 中的目标信息
        // 实际目标 Reminder 需要通过 ReminderMapper 查找
        // LA-005 简化：修改最近唯一 Reminder
        ReminderEntity reminder = reminderMapper.selectRecentByUserId(userId);
        if (reminder == null) {
            log.warn("修改提醒失败：无有效 Reminder, userId={}", userId);
            return "没有找到可修改的提醒。";
        }

        // 取消旧 Job
        scheduledJobMapper.cancelByReminder(reminder.getId(), now);

        // 更新内容或时间
        String newContent = resolution.getContent();
        Instant newRemindAt = resolution.getRemindAt();

        if (newContent != null) {
            reminder.setContent(newContent);
        }
        if (newRemindAt != null) {
            if (newRemindAt.isBefore(now)) {
                return "修改后的时间已过去，请重新设置。";
            }
            reminder.setEventAt(resolution.getEventAt());
            // 主提醒时间更新需要版本乐观锁
        }

        // 版本更新
        int updated = reminderMapper.updateStatusWithVersion(
                reminder.getId(), ReminderStatus.ACTIVE.name(),
                reminder.getStatus(), reminder.getVersion(), now);
        if (updated == 0) {
            log.warn("修改提醒版本冲突, reminderId={}", reminder.getId());
            return "提醒状态已变化，请重新查看后修改。";
        }

        // 创建新 Job
        Instant remindAt = newRemindAt != null ? newRemindAt : reminder.getEventAt();
        if (remindAt != null && remindAt.isAfter(now)) {
            String bizKey = "REMINDER_DELIVERY:" + reminder.getId() + ":PRIMARY";
            createJob(reminder.getId(), NodeType.PRIMARY.name(), bizKey, remindAt, remindAt, now);
        }

        log.info("提醒修改成功, reminderId={}", reminder.getId());
        return "已修改，新的提醒时间已生效。";
    }

    // ==================== ACK ====================

    @Transactional
    String handleAck(Long userId, ReminderResolution resolution, Instant now) {
        ReminderEntity reminder = reminderMapper.selectRecentByUserId(userId);
        if (reminder == null) {
            return null;
        }

        if (!ReminderStatus.ACTIVE.name().equals(reminder.getStatus())
                && !ReminderStatus.DELIVERED.name().equals(reminder.getStatus())) {
            return null;
        }

        int updated = reminderMapper.updateStatusWithVersion(
                reminder.getId(), ReminderStatus.ACKNOWLEDGED.name(),
                reminder.getStatus(), reminder.getVersion(), now);
        if (updated == 0) {
            log.warn("ACK 更新失败（版本或状态不符）, reminderId={}", reminder.getId());
        }
        return "好的，已记下。";
    }

    // ==================== 完成 ====================

    @Transactional
    String handleComplete(Long userId, ReminderResolution resolution, Instant now) {
        ReminderEntity reminder = reminderMapper.selectRecentByUserId(userId);
        if (reminder == null) {
            return null;
        }

        if (!ReminderStatus.ACTIVE.name().equals(reminder.getStatus())
                && !ReminderStatus.ACKNOWLEDGED.name().equals(reminder.getStatus())) {
            return null;
        }

        // 取消未来 Job
        scheduledJobMapper.cancelByReminder(reminder.getId(), now);

        int updated = reminderMapper.updateStatusWithVersion(
                reminder.getId(), ReminderStatus.COMPLETED.name(),
                reminder.getStatus(), reminder.getVersion(), now);
        if (updated == 0) {
            log.warn("COMPLETE 更新失败（版本或状态不符）, reminderId={}", reminder.getId());
            return null;
        }

        log.info("提醒已完成, reminderId={}", reminder.getId());
        return "好的，已标记为完成。";
    }

    // ==================== 稍后提醒 ====================

    @Transactional
    String handleSnooze(Long userId, ReminderResolution resolution, Instant now) {
        ReminderEntity reminder = reminderMapper.selectRecentByUserId(userId);
        if (reminder == null) {
            return null;
        }

        Instant snoozeAt = resolution.getRemindAt();
        if (snoozeAt == null) {
            // 默认半小时后
            snoozeAt = now.plusSeconds(1800);
        }

        if (snoozeAt.isBefore(now)) {
            snoozeAt = now.plusSeconds(1800);
        }

        // 取消旧 Job
        scheduledJobMapper.cancelByReminder(reminder.getId(), now);

        // 创建 SNOOZE Job
        String bizKey = "REMINDER_DELIVERY:" + reminder.getId() + ":SNOOZE:" + now.toEpochMilli();
        createJob(reminder.getId(), NodeType.SNOOZE.name(), bizKey, snoozeAt, snoozeAt, now);

        // 确保 Reminder 回到 ACTIVE
        if (!ReminderStatus.ACTIVE.name().equals(reminder.getStatus())) {
            reminderMapper.updateStatusWithVersion(
                    reminder.getId(), ReminderStatus.ACTIVE.name(),
                    reminder.getStatus(), reminder.getVersion(), now);
        }

        log.info("提醒已稍后, reminderId={}, snoozeAt={}", reminder.getId(), snoozeAt);
        return String.format("好的，我会在 %s 再次提醒你。", formatInstant(snoozeAt, "Asia/Shanghai"));
    }

    // ==================== 取消 ====================

    @Transactional
    String handleCancel(Long userId, ReminderResolution resolution, Instant now) {
        ReminderEntity reminder = reminderMapper.selectRecentByUserId(userId);
        if (reminder == null) {
            return null;
        }

        if (!ReminderStatus.ACTIVE.name().equals(reminder.getStatus())
                && !ReminderStatus.ACKNOWLEDGED.name().equals(reminder.getStatus())
                && !ReminderStatus.DELIVERED.name().equals(reminder.getStatus())) {
            return "该提醒当前状态不允许取消。";
        }

        // 取消未来 Job
        scheduledJobMapper.cancelByReminder(reminder.getId(), now);

        int updated = reminderMapper.updateStatusWithVersion(
                reminder.getId(), ReminderStatus.CANCELLED.name(),
                reminder.getStatus(), reminder.getVersion(), now);
        if (updated == 0) {
            log.warn("CANCEL 更新失败（版本或状态不符）, reminderId={}", reminder.getId());
            return null;
        }

        log.info("提醒已取消, reminderId={}", reminder.getId());
        return "好的，已取消该提醒。";
    }

    // ==================== 工具 ====================

    private void createJob(Long reminderId, String nodeType, String businessKey,
                           Instant scheduledAt, Instant nextRunAt, Instant now) {
        ScheduledJobEntity job = new ScheduledJobEntity();
        job.setJobType(JobType.REMINDER_DELIVERY.name());
        job.setBusinessKey(businessKey);
        job.setReminderId(reminderId);
        job.setNodeType(nodeType);
        job.setScheduledAt(scheduledAt);
        job.setNextRunAt(nextRunAt);
        job.setStatus("READY");
        job.setRetryCount(0);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        int rows = scheduledJobMapper.insertIgnore(job);
        if (rows == 0) {
            log.warn("Job 已存在（幂等跳过）, businessKey={}", businessKey);
        } else {
            log.debug("Job 创建成功, businessKey={}, jobId={}", businessKey, job.getId());
        }
    }

    private String formatInstant(Instant instant, String timezone) {
        ZonedDateTime zdt = instant.atZone(ZoneId.of(timezone));
        return zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
