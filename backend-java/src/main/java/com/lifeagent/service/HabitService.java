package com.lifeagent.service;

import com.lifeagent.dto.turn.HabitResolution;
import com.lifeagent.entity.HabitEntity;
import com.lifeagent.entity.HabitExecutionEntity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 日常习惯业务服务。
 */
public interface HabitService {

    /**
     * 根据 AI 习惯候选执行写操作。
     *
     * @param userId         用户 ID
     * @param sourceMessageId 创建来源 USER 消息 ID（幂等键）
     * @param idempotencyKey 回复 ASSISTANT 的幂等键
     * @param resolution     AI 返回的习惯候选
     * @param clock          时钟
     * @return 回复文案，null 表示不生成额外回复
     */
    String executeHabitAction(Long userId, Long sourceMessageId,
                               String idempotencyKey,
                               HabitResolution resolution, Clock clock);

    /**
     * 查询用户未过期 Redis 习惯候选，用于构造上下文。
     */
    com.lifeagent.cache.model.HabitDraftCacheValue findPendingDraft(Long userId);

    /**
     * 查询用户最近 ACTIVE/PAUSED 习惯，用于构造上下文。
     */
    List<HabitEntity> findRecentHabits(Long userId);

    /**
     * 查询用户最近一次已发送但未完成的执行，用于构造上下文。
     */
    HabitExecutionEntity findRecentExecution(Long userId);

    /**
     * 构建习惯到期提醒文案。
     */
    String buildDeliveryMessage(String habitName);

    /**
     * 为 ACTIVE 习惯补齐下一执行（调度入口）。
     *
     * @return 创建的执行数
     */
    int scheduleNextExecutions(Instant now);

    /**
     * 为特定习惯立即补齐下一执行。
     */
    void scheduleForHabit(Long habitId, Instant now);
}
