package com.lifeagent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeagent.cache.HabitDraftCache;
import com.lifeagent.cache.model.HabitDraftCacheValue;
import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.HabitResolution;
import com.lifeagent.entity.HabitEntity;
import com.lifeagent.entity.HabitExecutionEntity;
import com.lifeagent.entity.ScheduledJobEntity;
import com.lifeagent.enums.ExecutionStatus;
import com.lifeagent.enums.HabitStatus;
import com.lifeagent.enums.JobType;
import com.lifeagent.enums.NodeType;
import com.lifeagent.mapper.HabitExecutionMapper;
import com.lifeagent.mapper.HabitMapper;
import com.lifeagent.mapper.ScheduledJobMapper;
import com.lifeagent.service.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.lifeagent.common.Constants.*;

/**
 * 日常习惯业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HabitServiceImpl implements HabitService {

    private final HabitMapper habitMapper;
    private final HabitExecutionMapper habitExecutionMapper;
    private final ScheduledJobMapper scheduledJobMapper;
    private final HabitDraftCache habitDraftCache;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    // ==================== 主入口 ====================

    @Override
    public String executeHabitAction(Long userId, Long sourceMessageId,
                                      String idempotencyKey,
                                      HabitResolution resolution, Clock clock) {
        String action = resolution.getAction();
        Instant now = Instant.now(clock);

        return switch (action) {
            case HABIT_ACTION_UPSERT_DRAFT -> handleUpsertDraft(userId, resolution, now);
            case HABIT_ACTION_CONFIRM_DRAFT -> handleConfirmDraft(userId, sourceMessageId, resolution, now);
            case HABIT_ACTION_ACK -> handleAck(userId, resolution, now);
            case HABIT_ACTION_COMPLETE -> handleComplete(userId, resolution, sourceMessageId, now);
            case HABIT_ACTION_PAUSE -> handlePause(userId, resolution, now);
            case HABIT_ACTION_RESUME -> handleResume(userId, resolution, now);
            case HABIT_ACTION_CANCEL -> handleCancel(userId, resolution, now);
            default -> {
                log.warn("未知习惯操作: {}", action);
                yield null;
            }
        };
    }

    // ==================== 上下文查询 ====================

    @Override
    public HabitDraftCacheValue findPendingDraft(Long userId) {
        return habitDraftCache.get(userId);
    }

    @Override
    public List<HabitEntity> findRecentHabits(Long userId) {
        return habitMapper.selectRecentByUserId(userId, HABIT_RECENT_MAX_COUNT);
    }

    @Override
    public HabitExecutionEntity findRecentExecution(Long userId) {
        return habitExecutionMapper.selectUserRecentDelivered(userId);
    }

    @Override
    public String buildDeliveryMessage(String habitName) {
        return HABIT_MESSAGE_PREFIX + habitName + "。完成后告诉我一声。";
    }

    // ==================== Redis 候选操作 ====================

    /**
     * 处理 UPSERT_DRAFT：保存或更新 Redis 习惯候选。
     */
    String handleUpsertDraft(Long userId, HabitResolution resolution, Instant now) {
        HabitDraftCacheValue draft = habitDraftCache.get(userId);
        String userTimezone = "Asia/Shanghai"; // P0 固定值

        if (draft == null) {
            // 新建候选
            draft = new HabitDraftCacheValue();
            draft.setDraftToken(UUID.randomUUID().toString().replace("-", ""));
            draft.setSourceMessageId(0L);
            draft.setName(resolution.getName());
            draft.setDailyTimes(validateAndSortDailyTimes(resolution.getDailyTimes()));
            draft.setStartDate(resolution.getStartDate() != null
                    ? resolution.getStartDate() : LocalDate.now(ZoneId.of(userTimezone)));
            draft.setEndDate(resolution.getEndDate());
            draft.setTimezone(userTimezone);
            draft.setDraftStatus(HABIT_DRAFT_STATUS_AWAITING_CONFIRMATION);
            draft.setMissingFields(new ArrayList<>());
        } else {
            // 更新现有候选
            if (resolution.getName() != null && !resolution.getName().isBlank()) {
                draft.setName(resolution.getName());
            }
            if (resolution.getDailyTimes() != null && !resolution.getDailyTimes().isEmpty()) {
                draft.setDailyTimes(validateAndSortDailyTimes(resolution.getDailyTimes()));
            }
            if (resolution.getStartDate() != null) {
                draft.setStartDate(resolution.getStartDate());
            }
            if (resolution.getEndDate() != null) {
                draft.setEndDate(resolution.getEndDate());
            }
            draft.setDraftStatus(HABIT_DRAFT_STATUS_AWAITING_CONFIRMATION);
        }

        // 校验必填字段
        List<String> missing = new ArrayList<>();
        if (draft.getName() == null || draft.getName().isBlank()) {
            missing.add("name");
        }
        if (draft.getDailyTimes() == null || draft.getDailyTimes().isEmpty()) {
            missing.add("daily_times");
        }
        draft.setMissingFields(missing);

        boolean saved = habitDraftCache.save(userId, draft);
        if (!saved) {
            log.warn("习惯候选保存失败，不声称已记录, userId={}", userId);
            return "暂时无法记录待确认习惯，请稍后完整重述。";
        }

        if (!missing.isEmpty()) {
            // 字段不全，由 AI 继续追问
            return null;
        }

        // 字段完整，写候选成功（AI draft 作为复述）
        return null;
    }

    /**
     * 处理 CONFIRM_DRAFT：确认 Redis 候选，创建正式 Habit。
     */
    @Transactional
    String handleConfirmDraft(Long userId, Long sourceMessageId,
                               HabitResolution resolution, Instant now) {
        HabitDraftCacheValue draft = habitDraftCache.get(userId);
        if (draft == null) {
            log.warn("Redis 候选不存在或过期，无法确认, userId={}", userId);
            return "待确认的习惯已过期，请重新说明。";
        }

        String name = draft.getName();
        List<String> dailyTimes = draft.getDailyTimes();
        LocalDate startDate = draft.getStartDate();
        LocalDate endDate = draft.getEndDate();
        String timezone = draft.getTimezone() != null ? draft.getTimezone() : "Asia/Shanghai";

        if (name == null || dailyTimes == null || dailyTimes.isEmpty()) {
            log.warn("候选数据不完整, userId={}", userId);
            return "待确认的习惯信息不完整，请重新说明。";
        }

        // 序列化 daily_times 为 JSON 字符串
        String dailyTimesJson;
        try {
            dailyTimesJson = objectMapper.writeValueAsString(dailyTimes);
        } catch (JsonProcessingException e) {
            log.warn("daily_times 序列化失败, userId={}", userId);
            return "处理习惯信息时出错，请重试。";
        }

        // 如果确认时当天所有提醒已过去，改为明天
        ZoneId systemZone = ZoneId.systemDefault();
        ZoneId userZone = ZoneId.of(timezone);
        LocalDate today = now.atZone(systemZone).withZoneSameInstant(userZone).toLocalDate();
        if (startDate == null || startDate.isBefore(today)) {
            startDate = today;
        }
        // 检查当天是否还有未来的提醒时刻
        LocalDate effectiveStart = startDate;
        if (effectiveStart.equals(today)) {
            boolean hasFutureToday = hasFutureTimeToday(dailyTimes, userZone, now);
            if (!hasFutureToday) {
                effectiveStart = today.plusDays(1);
            }
        }

        // 创建 Habit
        HabitEntity habit = new HabitEntity();
        habit.setUserId(userId);
        habit.setName(name);
        habit.setDailyTimes(dailyTimesJson);
        habit.setTimezone(timezone);
        habit.setStartDate(effectiveStart);
        habit.setEndDate(endDate);
        habit.setStatus(HabitStatus.ACTIVE.name());
        habit.setSourceMessageId(sourceMessageId);
        habit.setVersion(1);
        habit.setCreatedAt(now);
        habit.setUpdatedAt(now);

        int rows = habitMapper.insertIgnore(habit);
        if (rows == 0) {
            log.warn("习惯已存在（幂等跳过）, userId={}, sourceMessageId={}", userId, sourceMessageId);
            return "该习惯已经设置过了。";
        }

        // 创建第一个 Execution + Job
        createNextExecution(habit.getId(), dailyTimes, timezone, effectiveStart, now);

        // 删除 Redis 候选
        habitDraftCache.deleteIfTokenMatches(userId, draft.getDraftToken());

        String timeSummary = dailyTimes.stream()
                .collect(Collectors.joining("、"));
        String endStr = endDate != null ? "到 " + endDate + " 结束" : "持续生效，直到你暂停或取消";

        log.info("习惯创建成功, habitId={}, userId={}, name={}", habit.getId(), userId, name);
        return String.format("已创建\"%s\"习惯，每天 %s 提醒你，%s。", name, timeSummary, endStr);
    }

    // ==================== ACK ====================

    @Transactional
    String handleAck(Long userId, HabitResolution resolution, Instant now) {
        HabitExecutionEntity execution = findTargetExecution(userId, resolution);
        if (execution == null) {
            return null;
        }

        if (!ExecutionStatus.DELIVERED.name().equals(execution.getStatus())
                && !ExecutionStatus.ACKNOWLEDGED.name().equals(execution.getStatus())) {
            return null;
        }

        int updated = habitExecutionMapper.updateStatusWithVersion(
                execution.getId(), ExecutionStatus.ACKNOWLEDGED.name(),
                execution.getStatus(), execution.getVersion(), now);
        if (updated > 0) {
            log.info("习惯执行 ACK, executionId={}", execution.getId());
        }
        return "好的，已记下。";
    }

    // ==================== 完成 ====================

    @Transactional
    String handleComplete(Long userId, HabitResolution resolution,
                           Long sourceMessageId, Instant now) {
        HabitExecutionEntity execution = findTargetExecution(userId, resolution);
        if (execution == null) {
            return null;
        }

        if (!ExecutionStatus.DELIVERED.name().equals(execution.getStatus())
                && !ExecutionStatus.ACKNOWLEDGED.name().equals(execution.getStatus())) {
            return null;
        }

        // 幂等检查：completion_message_id 唯一
        if (sourceMessageId != null && sourceMessageId > 0) {
            HabitExecutionEntity existing = habitExecutionMapper.selectByCompletionMessage(sourceMessageId);
            if (existing != null) {
                log.warn("完成消息已使用（幂等跳过）, sourceMessageId={}", sourceMessageId);
                return "该完成请求已处理过。";
            }
        }

        // 更新执行状态，由 XML 自动设置 completed_at
        int updated = habitExecutionMapper.updateStatusWithVersion(
                execution.getId(), ExecutionStatus.COMPLETED.name(),
                execution.getStatus(), execution.getVersion(), now);
        if (updated == 0) {
            log.warn("完成更新失败（版本或状态不符）, executionId={}", execution.getId());
            return null;
        }

        // 补填 completion_message_id
        if (sourceMessageId != null && sourceMessageId > 0) {
            HabitExecutionEntity fresh = habitExecutionMapper.selectById(execution.getId());
            if (fresh != null) {
                fresh.setCompletionMessageId(sourceMessageId);
                fresh.setUpdatedAt(now);
                habitExecutionMapper.updateById(fresh);
            }
        }

        log.info("习惯执行完成, executionId={}", execution.getId());
        return "好的，已为你记录完成！继续保持！";
    }

    // ==================== 暂停 ====================

    @Transactional
    String handlePause(Long userId, HabitResolution resolution, Instant now) {
        HabitEntity habit = findTargetHabit(userId, resolution);
        if (habit == null) {
            return null;
        }

        if (!HabitStatus.ACTIVE.name().equals(habit.getStatus())) {
            return "该习惯当前状态不允许暂停。";
        }

        // 取消未来 Job
        scheduledJobMapper.cancelByHabit(habit.getId(), now);

        // 取消未来 Execution
        habitExecutionMapper.cancelByHabit(habit.getId(), now);

        int updated = habitMapper.updateStatusWithVersion(
                habit.getId(), HabitStatus.PAUSED.name(),
                habit.getStatus(), habit.getVersion(), now);
        if (updated == 0) {
            log.warn("暂停失败（版本或状态不符）, habitId={}", habit.getId());
            return null;
        }

        log.info("习惯已暂停, habitId={}", habit.getId());
        return "已暂停\"" + habit.getName() + "\"习惯，暂停期间不会提醒你。";
    }

    // ==================== 恢复 ====================

    @Transactional
    String handleResume(Long userId, HabitResolution resolution, Instant now) {
        HabitEntity habit = findTargetHabit(userId, resolution);
        if (habit == null) {
            return null;
        }

        if (!HabitStatus.PAUSED.name().equals(habit.getStatus())) {
            return "该习惯当前状态不允许恢复。";
        }

        int updated = habitMapper.updateStatusWithVersion(
                habit.getId(), HabitStatus.ACTIVE.name(),
                habit.getStatus(), habit.getVersion(), now);
        if (updated == 0) {
            log.warn("恢复失败（版本或状态不符）, habitId={}", habit.getId());
            return null;
        }

        // 提交事务后补齐下一执行（事务内完成）
        scheduleNextExecutionForHabit(habit, now);

        log.info("习惯已恢复, habitId={}", habit.getId());
        return "已恢复\"" + habit.getName() + "\"习惯，将从下一提醒时刻继续提醒你。";
    }

    // ==================== 取消 ====================

    @Transactional
    String handleCancel(Long userId, HabitResolution resolution, Instant now) {
        // 如果目标是 PENDING_DRAFT + CANCEL，只删除候选
        if (HABIT_TARGET_PENDING_DRAFT.equals(resolution.getTarget())) {
            habitDraftCache.delete(userId);
            log.info("已删除习惯候选, userId={}", userId);
            return "已取消习惯设置。";
        }

        HabitEntity habit = findTargetHabit(userId, resolution);
        if (habit == null) {
            return null;
        }

        if (!HabitStatus.ACTIVE.name().equals(habit.getStatus())
                && !HabitStatus.PAUSED.name().equals(habit.getStatus())) {
            return "该习惯当前状态不允许取消。";
        }

        // 取消未来 Job
        scheduledJobMapper.cancelByHabit(habit.getId(), now);

        // 取消未来 Execution
        habitExecutionMapper.cancelByHabit(habit.getId(), now);

        int updated = habitMapper.updateStatusWithVersion(
                habit.getId(), HabitStatus.CANCELLED.name(),
                habit.getStatus(), habit.getVersion(), now);
        if (updated == 0) {
            log.warn("取消失败（版本或状态不符）, habitId={}", habit.getId());
            return null;
        }

        log.info("习惯已取消, habitId={}", habit.getId());
        return "已取消\"" + habit.getName() + "\"习惯，历史记录已保留。";
    }

    // ==================== 调度补齐 ====================

    @Override
    public int scheduleNextExecutions(Instant now) {
        List<HabitEntity> activeHabits = habitMapper.selectActiveForScheduling(now, "Asia/Shanghai");
        int created = 0;

        for (HabitEntity habit : activeHabits) {
            try {
                if (scheduleNextExecutionForHabit(habit, now)) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("习惯补齐异常, habitId={}", habit.getId(), e);
            }
        }

        // 检查是否需要标记 ENDED
        markEndedHabits(now);

        if (created > 0) {
            log.info("习惯补齐完成, created={}, total={}", created, activeHabits.size());
        }
        return created;
    }

    @Override
    public void scheduleForHabit(Long habitId, Instant now) {
        HabitEntity habit = habitMapper.selectById(habitId);
        if (habit == null) {
            log.warn("习惯不存在, habitId={}", habitId);
            return;
        }
        scheduleNextExecutionForHabit(habit, now);
    }

    // ==================== 私有方法 ====================

    /**
     * 为单个习惯创建下一执行（如果不存在）。
     */
    private boolean scheduleNextExecutionForHabit(HabitEntity habit, Instant now) {
        String timezone = habit.getTimezone() != null ? habit.getTimezone() : "Asia/Shanghai";
        List<String> dailyTimes = parseDailyTimes(habit.getDailyTimes());
        if (dailyTimes == null || dailyTimes.isEmpty()) {
            return false;
        }

        ZoneId userZone = ZoneId.of(timezone);
        LocalDate today = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(userZone).toLocalDate();

        // 从开始日期到结束日期之间查找
        LocalDate start = habit.getStartDate();
        LocalDate end = habit.getEndDate();

        // 已超过结束日期，标记 ENDED
        if (end != null && end.isBefore(today)) {
            markHabitEnded(habit, now);
            return false;
        }

        // 尝试今天的后续时刻和之后的日期
        LocalDate checkDate = today;
        while (end == null || !checkDate.isAfter(end)) {
            for (String timeStr : dailyTimes) {
                String occurrenceKey = checkDate + "#" + timeStr;
                LocalTime time;
                try {
                    time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                } catch (DateTimeParseException e) {
                    continue;
                }
                ZonedDateTime scheduledZdt = ZonedDateTime.of(checkDate, time, userZone);
                Instant scheduledAt = scheduledZdt.toInstant();

                // 只创建严格晚于当前时间的节点
                if (scheduledAt.isAfter(now)) {
                    // 事务内幂等创建
                    HabitExecutionEntity execution = new HabitExecutionEntity();
                    execution.setHabitId(habit.getId());
                    execution.setOccurrenceKey(occurrenceKey);
                    execution.setScheduledAt(scheduledAt);
                    execution.setStatus(ExecutionStatus.SCHEDULED.name());
                    execution.setVersion(1);
                    execution.setCreatedAt(now);
                    execution.setUpdatedAt(now);

                    int execRows = habitExecutionMapper.insertIgnore(execution);
                    if (execRows > 0) {
                        // 创建投递 Job
                        String bizKey = "HABIT_DELIVERY:" + habit.getId() + ":" + occurrenceKey;
                        ScheduledJobEntity job = new ScheduledJobEntity();
                        job.setJobType(JobType.HABIT_DELIVERY.name());
                        job.setBusinessKey(bizKey);
                        job.setHabitExecutionId(execution.getId());
                        job.setNodeType(NodeType.PRIMARY.name());
                        job.setScheduledAt(scheduledAt);
                        job.setNextRunAt(scheduledAt);
                        job.setStatus("READY");
                        job.setRetryCount(0);
                        job.setCreatedAt(now);
                        job.setUpdatedAt(now);

                        int jobRows = scheduledJobMapper.insertIgnoreHabit(job);
                        if (jobRows > 0) {
                            log.debug("习惯执行已调度, habitId={}, occurrenceKey={}, executionId={}",
                                    habit.getId(), occurrenceKey, execution.getId());
                            return true;
                        }
                    }
                }
            }
            // 下一天
            checkDate = checkDate.plusDays(1);
            // 最多查找 31 天避免无限循环
            if (checkDate.isAfter(today.plusDays(31))) {
                break;
            }
        }

        return false;
    }

    /**
     * 创建首个 Execution（用于确认创建时立即补齐）。
     */
    private void createNextExecution(Long habitId, List<String> dailyTimes,
                                      String timezone, LocalDate startDate, Instant now) {
        HabitEntity habit = habitMapper.selectById(habitId);
        if (habit != null) {
            scheduleNextExecutionForHabit(habit, now);
        }
    }

    /**
     * 查找目标执行（最近一次已发送但未完成）。
     */
    private HabitExecutionEntity findTargetExecution(Long userId, HabitResolution resolution) {
        // 如果有 habitId，按 habit 查找
        if (resolution.getHabitId() != null) {
            return habitExecutionMapper.selectRecentDelivered(resolution.getHabitId());
        }
        // 否则查询用户最近执行
        return habitExecutionMapper.selectUserRecentDelivered(userId);
    }

    /**
     * 查找目标 Habit：按 ID、名称或最近原则。
     */
    private HabitEntity findTargetHabit(Long userId, HabitResolution resolution) {
        // 优先按 ID 查找
        if (resolution.getHabitId() != null) {
            HabitEntity habit = habitMapper.selectById(resolution.getHabitId());
            if (habit != null && habit.getUserId().equals(userId)) {
                return habit;
            }
        }

        // 按名称唯一匹配
        if (resolution.getName() != null && !resolution.getName().isBlank()) {
            List<HabitEntity> matched = habitMapper.selectByName(userId, resolution.getName());
            if (matched.size() == 1) {
                return matched.get(0);
            }
            if (matched.size() > 1) {
                log.warn("习惯名称不唯一, userId={}, name={}, count={}", userId, resolution.getName(), matched.size());
                return null;
            }
        }

        // 回退到最近习惯
        List<HabitEntity> recent = habitMapper.selectRecentByUserId(userId, 1);
        if (!recent.isEmpty()) {
            return recent.get(0);
        }

        log.warn("未找到目标习惯, userId={}", userId);
        return null;
    }

    /**
     * 解析 daily_times JSON 字符串为字符串列表。
     */
    private List<String> parseDailyTimes(String dailyTimesJson) {
        if (dailyTimesJson == null || dailyTimesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(dailyTimesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("daily_times 解析失败", e);
            return List.of();
        }
    }

    /**
     * 校验、排序、去重并限制最多 10 个提醒时刻。
     */
    private List<String> validateAndSortDailyTimes(List<String> times) {
        if (times == null) {
            return List.of();
        }
        return times.stream()
                .map(String::trim)
                .filter(t -> t.matches("^([01]\\d|2[0-3]):[0-5]\\d$"))
                .distinct()
                .sorted()
                .limit(HABIT_MAX_DAILY_TIMES)
                .collect(Collectors.toList());
    }

    /**
     * 检查当天是否还有未来的提醒时刻。
     */
    private boolean hasFutureTimeToday(List<String> dailyTimes, ZoneId userZone, Instant now) {
        LocalDate today = now.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(userZone).toLocalDate();
        ZonedDateTime nowInZone = now.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(userZone);

        for (String timeStr : dailyTimes) {
            try {
                LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                ZonedDateTime scheduledZdt = ZonedDateTime.of(today, time, userZone);
                if (scheduledZdt.toInstant().isAfter(now)) {
                    return true;
                }
            } catch (DateTimeParseException e) {
                // ignore
            }
        }
        return false;
    }

    /**
     * 标记超过结束日期的习惯为 ENDED。
     */
    private void markEndedHabits(Instant now) {
        // 简单处理：查询 ACTIVE 状态且 end_date 已过去的习惯
        List<HabitEntity> habits = habitMapper.selectActiveForScheduling(now, "Asia/Shanghai");
        LocalDate today = now.atZone(ZoneId.systemDefault()).toLocalDate();

        for (HabitEntity habit : habits) {
            if (habit.getEndDate() != null && habit.getEndDate().isBefore(today)) {
                markHabitEnded(habit, now);
            }
        }
    }

    private void markHabitEnded(HabitEntity habit, Instant now) {
        int updated = habitMapper.updateStatusWithVersion(
                habit.getId(), HabitStatus.ENDED.name(),
                habit.getStatus(), habit.getVersion(), now);
        if (updated > 0) {
            // 取消未来 Job
            scheduledJobMapper.cancelByHabit(habit.getId(), now);
            log.info("习惯已标记 ENDED, habitId={}", habit.getId());
        }
    }
}
