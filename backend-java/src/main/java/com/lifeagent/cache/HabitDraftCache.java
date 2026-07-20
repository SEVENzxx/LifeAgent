package com.lifeagent.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeagent.cache.model.HabitDraftCacheValue;
import com.lifeagent.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.lifeagent.common.Constants.REDIS_HABIT_DRAFT_PREFIX;

/**
 * 习惯候选 Redis 缓存：每个用户最多一份，TTL 30 分钟。
 *
 * <p>候选不是正式业务事实，不过期不恢复、不回写 conversation_contexts。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HabitDraftCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    private String buildKey(Long userId) {
        return REDIS_HABIT_DRAFT_PREFIX + userId;
    }

    /**
     * 保存习惯候选到 Redis。
     *
     * @param userId 用户 ID
     * @param draft  候选值
     * @return true 表示写入成功
     */
    public boolean save(Long userId, HabitDraftCacheValue draft) {
        String key = buildKey(userId);
        try {
            String json = objectMapper.writeValueAsString(draft);
            stringRedisTemplate.opsForValue().set(
                    key, json, aiProperties.getHabitDraftTtlMinutes(), TimeUnit.MINUTES);
            log.info("习惯候选已保存, userId={}, draftToken={}", userId, draft.getDraftToken());
            return true;
        } catch (Exception e) {
            log.warn("习惯候选保存失败, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 读取用户的 Redis 习惯候选。
     *
     * @return 候选值，不存在或已过期时返回 null
     */
    public HabitDraftCacheValue get(Long userId) {
        String key = buildKey(userId);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, HabitDraftCacheValue.class);
        } catch (Exception e) {
            log.warn("习惯候选读取失败, userId={}", userId);
            return null;
        }
    }

    /**
     * 刷新 TTL。
     */
    public void refreshTtl(Long userId) {
        String key = buildKey(userId);
        try {
            stringRedisTemplate.expire(
                    key, aiProperties.getHabitDraftTtlMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("习惯候选 TTL 刷新失败, userId={}", userId);
        }
    }

    /**
     * 条件删除候选（只有 draft_token 一致时才删除）。
     */
    public boolean deleteIfTokenMatches(Long userId, String expectedToken) {
        String key = buildKey(userId);
        try {
            HabitDraftCacheValue current = get(userId);
            if (current != null && current.getDraftToken().equals(expectedToken)) {
                stringRedisTemplate.delete(key);
                log.info("习惯候选已删除, userId={}, draftToken={}", userId, expectedToken);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("习惯候选删除失败, userId={}", userId);
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
            log.warn("习惯候选删除失败, userId={}", userId);
        }
    }
}
