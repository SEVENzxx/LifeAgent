package com.lifeagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.ContextMessageItem;
import com.lifeagent.dto.turn.ContextPackage;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.lifeagent.common.Constants.*;

/**
 * 上下文服务实现：Redis Cache-Aside + SQL 回源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextServiceImpl implements ContextService {

    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationContextMapper conversationContextMapper;
    private final ChannelBindingMapper channelBindingMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public ContextPackage getOrBuildContext(Long userId, String currentMessage) {
        String cacheKey = REDIS_CONTEXT_PREFIX + userId;
        String cached = null;
        boolean cacheHit = false;
        try {
            cached = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 不可用, userId={}", userId);
        }

        if (cached != null && !cached.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(cached);
                if (isValidCache(root)) {
                    cacheHit = true;
                    return buildFromCache(root, currentMessage);
                }
            } catch (Exception e) {
                log.warn("Redis 缓存格式非法, userId={}", userId);
            }
        }

        log.info("上下文缓存未命中, userId={}", userId);
        return buildFromSql(userId, currentMessage, cacheKey);
    }

    @Override
    @Transactional
    public void updateAfterResolution(Long userId, ContextPackage context, TurnResolutionResponse response) {
        String status = response.getResolutionStatus();
        if (STATUS_AI_UNAVAILABLE.equals(status)) {
            log.info("AI 不可用，不更新上下文状态, userId={}", userId);
            return;
        }
        // 摘要和 Intent 更新统一由 ReplyService 完成
        // 此处只做日志和基础校验
    }

    @Override
    public void evictCache(Long userId) {
        String cacheKey = REDIS_CONTEXT_PREFIX + userId;
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 缓存失效失败, userId={}", userId);
        }
    }

    // ==================== 公开工具方法 ====================

    /**
     * 查询用户的所有可见历史消息（USER 已收到和 ASSISTANT 已发送）。
     */
    public List<ConversationMessageEntity> loadVisibleMessages(Long userId) {
        List<ChannelBindingEntity> bindings = channelBindingMapper.selectList(
                new LambdaQueryWrapper<ChannelBindingEntity>()
                        .eq(ChannelBindingEntity::getUserId, userId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> bindingIds = bindings.stream().map(ChannelBindingEntity::getId).toList();

        return conversationMessageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .in(ConversationMessageEntity::getChannelBindingId, bindingIds)
                        .and(w -> w
                                .and(w1 -> w1
                                        .eq(ConversationMessageEntity::getRole, MessageRole.USER.name())
                                        .eq(ConversationMessageEntity::getDeliveryStatus, DeliveryStatus.CREATED.name()))
                                .or(w2 -> w2
                                        .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT.name())
                                        .eq(ConversationMessageEntity::getDeliveryStatus, DeliveryStatus.SENT.name())))
                        .orderByAsc(ConversationMessageEntity::getId)
        );
    }

    /**
     * 选择近期窗口：最多 N 条、合计最多 N 字符、排除当前消息和未发送的 ASSISTANT。
     */
    public List<ContextMessageItem> selectRecentWindow(
            List<ConversationMessageEntity> allMessages, String currentMessage) {

        int maxMessages = aiProperties.getRecentMaxMessages();
        int maxChars = aiProperties.getRecentMaxChars();

        List<ContextMessageItem> result = new ArrayList<>();
        int totalChars = 0;

        for (int i = allMessages.size() - 1; i >= 0; i--) {
            ConversationMessageEntity msg = allMessages.get(i);
            if (msg.getContent().equals(currentMessage)) {
                continue;
            }

            int msgLen = msg.getContent().length();
            if (result.size() >= maxMessages || totalChars + msgLen > maxChars) {
                break;
            }

            result.add(ContextMessageItem.builder()
                    .messageId(msg.getId())
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
            totalChars += msgLen;
        }

        Collections.reverse(result);
        return result;
    }

    /**
     * 加载指定 ID 区间的消息。
     */
    public List<ContextMessageItem> loadMessagesBetween(
            Long afterId, Long throughId, int maxMessages, int maxChars) {

        if (afterId == null || throughId == null || throughId <= afterId) {
            return List.of();
        }

        List<ConversationMessageEntity> raw = conversationMessageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .gt(ConversationMessageEntity::getId, afterId)
                        .le(ConversationMessageEntity::getId, throughId)
                        .and(w -> w
                                .eq(ConversationMessageEntity::getRole, MessageRole.USER.name())
                                .or(w2 -> w2
                                        .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT.name())
                                        .eq(ConversationMessageEntity::getDeliveryStatus, DeliveryStatus.SENT.name())))
                        .orderByAsc(ConversationMessageEntity::getId)
        );

        List<ContextMessageItem> result = new ArrayList<>();
        int totalChars = 0;
        for (ConversationMessageEntity msg : raw) {
            int msgLen = msg.getContent().length();
            if (result.size() >= maxMessages || totalChars + msgLen > maxChars) {
                break;
            }
            result.add(ContextMessageItem.builder()
                    .messageId(msg.getId())
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
            totalChars += msgLen;
        }
        return result;
    }

    /**
     * 回填 Redis 上下文缓存。
     */
    public void refreshCache(Long userId, String cacheKey, String summary,
                             Long summarizedThroughId, List<ContextMessageItem> recentMessages,
                             int unsummarizedCount, int unsummarizedChars, long cachedThroughId) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schema_version", SCHEMA_VERSION);
            root.put("summary", summary);
            if (summarizedThroughId != null) {
                root.put("summarized_through_message_id", summarizedThroughId);
            } else {
                root.putNull("summarized_through_message_id");
            }
            root.put("cached_through_message_id", cachedThroughId);

            ArrayNode recentArray = root.putArray("recent_messages");
            for (ContextMessageItem item : recentMessages) {
                ObjectNode msgObj = recentArray.addObject();
                msgObj.put("message_id", item.getMessageId());
                msgObj.put("role", item.getRole());
                msgObj.put("content", item.getContent());
            }
            root.put("unsummarized_message_count", unsummarizedCount);
            root.put("unsummarized_char_count", unsummarizedChars);

            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(root),
                    aiProperties.getContextCacheTtlSeconds(),
                    TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("序列化上下文缓存失败, userId={}", userId);
        }
    }

    // ==================== 私有方法 ====================

    private boolean isValidCache(JsonNode root) {
        if (!root.has("schema_version")) {
            return false;
        }
        return SCHEMA_VERSION.equals(root.get("schema_version").asText(""));
    }

    private ContextPackage buildFromCache(JsonNode root, String currentMessage) {
        String summary = root.has("summary") && !root.get("summary").isNull()
                ? root.get("summary").asText() : null;

        List<ContextMessageItem> recentMessages = new ArrayList<>();
        if (root.has("recent_messages") && root.get("recent_messages").isArray()) {
            for (JsonNode item : root.get("recent_messages")) {
                recentMessages.add(ContextMessageItem.builder()
                        .messageId(item.get("message_id").asLong())
                        .role(item.get("role").asText())
                        .content(item.get("content").asText())
                        .build());
            }
        }

        int unsummarizedCount = root.has("unsummarized_message_count")
                ? root.get("unsummarized_message_count").asInt(0) : 0;
        int unsummarizedChars = root.has("unsummarized_char_count")
                ? root.get("unsummarized_char_count").asInt(0) : 0;

        boolean summaryRequested = unsummarizedCount >= aiProperties.getSummaryTriggerMessages()
                || unsummarizedChars >= aiProperties.getSummaryTriggerChars();

        log.info("上下文缓存命中, unsummarizedCount={}, summaryRequested={}",
                unsummarizedCount, summaryRequested);

        List<ContextMessageItem> summaryMessages = List.of();
        if (summaryRequested) {
            long summarizedThrough = root.has("summarized_through_message_id")
                    && !root.get("summarized_through_message_id").isNull()
                    ? root.get("summarized_through_message_id").asLong(0) : 0;
            long cachedThrough = root.has("cached_through_message_id")
                    ? root.get("cached_through_message_id").asLong(0) : 0;

            if (summarizedThrough > 0 && cachedThrough > summarizedThrough) {
                summaryMessages = loadMessagesBetween(summarizedThrough, cachedThrough,
                        aiProperties.getSummaryTriggerMessages(), aiProperties.getSummaryTriggerChars());
            }
        }

        return ContextPackage.builder()
                .currentMessage(currentMessage)
                .memorySummary(summary)
                .recentMessages(recentMessages)
                .summaryRequested(summaryRequested)
                .summaryMessages(summaryMessages)
                .build();
    }

    private ContextPackage buildFromSql(Long userId, String currentMessage, String cacheKey) {
        ConversationContextEntity ctx = conversationContextMapper.selectById(userId);
        String summary = (ctx != null) ? ctx.getSummary() : null;
        Long summarizedThroughId = (ctx != null) ? ctx.getSummarizedThroughMessageId() : null;

        List<ConversationMessageEntity> allMessages = loadVisibleMessages(userId);
        List<ContextMessageItem> recentMessages = selectRecentWindow(allMessages, currentMessage);

        long lastMsgId = allMessages.isEmpty() ? 0 : allMessages.get(allMessages.size() - 1).getId();

        int unsummarizedCount = 0;
        int unsummarizedChars = 0;
        if (summarizedThroughId != null && summarizedThroughId > 0) {
            for (ConversationMessageEntity msg : allMessages) {
                if (msg.getId() > summarizedThroughId) {
                    boolean inRecentWindow = recentMessages.stream()
                            .anyMatch(r -> r.getMessageId().equals(msg.getId()));
                    if (!inRecentWindow) {
                        unsummarizedCount++;
                        unsummarizedChars += msg.getContent().length();
                    }
                }
            }
        }

        boolean summaryRequested = summarizedThroughId != null && summarizedThroughId > 0
                && (unsummarizedCount >= aiProperties.getSummaryTriggerMessages()
                || unsummarizedChars >= aiProperties.getSummaryTriggerChars());

        List<ContextMessageItem> summaryMessages = List.of();
        if (summaryRequested) {
            summaryMessages = loadMessagesBetween(summarizedThroughId, lastMsgId,
                    aiProperties.getSummaryTriggerMessages(), aiProperties.getSummaryTriggerChars())
                    .stream()
                    .filter(m -> recentMessages.stream().noneMatch(r -> r.getMessageId().equals(m.getMessageId())))
                    .collect(Collectors.toList());
        }

        try {
            refreshCache(userId, cacheKey, summary, summarizedThroughId,
                    recentMessages, unsummarizedCount, unsummarizedChars, lastMsgId);
        } catch (Exception e) {
            log.warn("回填 Redis 缓存失败, userId={}", userId);
        }

        log.info("上下文从 SQL 重建, userId={}, recentCount={}, summaryRequested={}, unsummarizedCount={}",
                userId, recentMessages.size(), summaryRequested, unsummarizedCount);

        return ContextPackage.builder()
                .currentMessage(currentMessage)
                .memorySummary(summary)
                .recentMessages(recentMessages)
                .summaryRequested(summaryRequested)
                .summaryMessages(summaryMessages)
                .build();
    }
}
