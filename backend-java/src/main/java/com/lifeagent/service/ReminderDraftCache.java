package com.lifeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.PendingReminderInfo;
import com.lifeagent.entity.ReminderDraftEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.lifeagent.common.Constants.REDIS_REMINDER_DRAFT_PREFIX;

/**
 * 提醒候选 Redis 缓存：每个用户最多一份，TTL 30 分钟。
 *
 * <p>候选不是正式业务事实，不回写 conversation_contexts，过期后不恢复。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderDraftCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    private String buildKey(Long userId) {
        return REDIS_REMINDER_DRAFT_PREFIX + userId;
    }

    /**
     * 保存候选到 Redis。
     *
     * @param userId 用户 ID
     * @param draft  候选实体
     * @return true 表示写入成功
     */
    public boolean save(Long userId, ReminderDraftEntity draft) {
        String key = buildKey(userId);
        try {
            String json = objectMapper.writeValueAsString(draft);
            stringRedisTemplate.opsForValue().set(
                    key, json, aiProperties.getReminderDraftTtlMinutes(), TimeUnit.MINUTES);
            log.info("提醒候选已保存, userId={}, draftToken={}", userId, draft.getDraftToken());
            return true;
        } catch (Exception e) {
            log.warn("提醒候选保存失败, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 读取用户的 Redis 候选。
     *
     * @return 候选实体，不存在或已过期时返回 null
     */
    public ReminderDraftEntity get(Long userId) {
        String key = buildKey(userId);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, ReminderDraftEntity.class);
        } catch (Exception e) {
            log.warn("提醒候选读取失败, userId={}", userId);
            return null;
        }
    }

    /**
     * 读取并转换为请求上下文中的 PendingReminderInfo。
     */
    public PendingReminderInfo getAsInfo(Long userId) {
        ReminderDraftEntity draft = get(userId);
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

    /**
     * 刷新 TTL。
     */
    public void refreshTtl(Long userId) {
        String key = buildKey(userId);
        try {
            stringRedisTemplate.expire(
                    key, aiProperties.getReminderDraftTtlMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("提醒候选 TTL 刷新失败, userId={}", userId);
        }
    }

    /**
     * 条件删除候选（只有 draft_token 一致时才删除）。
     */
    public boolean deleteIfTokenMatches(Long userId, String expectedToken) {
        String key = buildKey(userId);
        try {
            ReminderDraftEntity current = get(userId);
            if (current != null && current.getDraftToken().equals(expectedToken)) {
                stringRedisTemplate.delete(key);
                log.info("提醒候选已删除, userId={}, draftToken={}", userId, expectedToken);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("提醒候选删除失败, userId={}", userId);
            return false;
        }
    }

    /**
     * 直接删除候选（忽略 token 匹配）。
     */
    public void delete(Long userId) {
        String key = buildKey(userId);
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("提醒候选删除失败, userId={}", userId);
        }
    }
}
