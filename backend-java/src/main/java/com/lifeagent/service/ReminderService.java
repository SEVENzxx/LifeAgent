package com.lifeagent.service;

import com.lifeagent.dto.turn.ReminderResolution;
import com.lifeagent.entity.ReminderEntity;

import java.time.Clock;

/**
 * 提醒业务服务。
 */
public interface ReminderService {

    /**
     * 根据 AI 提醒候选执行写操作。
     *
     * @param userId         用户 ID
     * @param sourceMessageId 创建来源 USER 消息 ID（幂等键）
     * @param idempotencyKey 回复 ASSISTANT 的幂等键
     * @param resolution     AI 返回的提醒候选
     * @param clock          时钟
     * @return 回复文案，null 表示不生成额外回复
     */
    String executeReminderAction(Long userId, Long sourceMessageId,
                                  String idempotencyKey,
                                  ReminderResolution resolution, Clock clock);

    /**
     * 查询用户最近有效 Reminder，用于构造上下文。
     */
    ReminderEntity findRecentReminder(Long userId);

    /**
     * 查询已到期提醒的稳定文案。
     */
    String buildDeliveryMessage(String content);
}
