package com.lifeagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifeagent.common.BizException;
import com.lifeagent.dto.InboundMessageCommand;
import com.lifeagent.dto.InboundMessageRequest;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.dto.OutboundMessageResponse;
import com.lifeagent.entity.ChannelBindingEntity;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.entity.UserEntity;
import com.lifeagent.enums.DeliveryStatus;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.mapper.UserMapper;
import com.lifeagent.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.HexFormat;
import java.util.List;

/**
 * 会话服务实现，支持多渠道入站处理。
 *
 * <p>三表闭环：users、channel_bindings、conversation_messages。
 * Mock 渠道 ASSISTANT 直接 SENT，真实渠道 ASSISTANT 进入 CREATED 等待异步发送。
 * 幂等由 idempotency_key 唯一约束保证，Redis 只做预检优化。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String FIXED_REPLY_TEXT = "收到，我已经记录这条消息。";
    static final String MOCK_CHANNEL = "MOCK";

    private final UserMapper userMapper;
    private final ChannelBindingMapper channelBindingMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    @Override
    @Transactional
    public InboundMessageResponse receiveInboundMessage(InboundMessageRequest request) {
        InboundMessageCommand command = InboundMessageCommand.builder()
                .channel(MOCK_CHANNEL)
                .externalUserId(request.getExternalUserId().trim())
                .externalMessageId(request.getExternalMessageId().trim())
                .text(request.getText().trim())
                .sentAt(request.getSentAt())
                .build();
        return processInboundMessage(command);
    }

    @Override
    @Transactional
    public InboundMessageResponse processInboundMessage(InboundMessageCommand command) {
        String channel = command.getChannel();
        String externalUserId = command.getExternalUserId().trim();
        String externalMessageId = command.getExternalMessageId().trim();
        String idempotencyKey = channel + ":" + externalMessageId;
        String text = command.getText().trim();
        LocalDateTime now = LocalDateTime.now(clock);

        // 检查 sentAt 是否晚于服务器时间 10 分钟以上
        OffsetDateTime sentAt = command.getSentAt();
        if (sentAt != null) {
            OffsetDateTime serverNow = OffsetDateTime.now(clock);
            if (sentAt.isAfter(serverNow.plusMinutes(10))) {
                throw new BizException("消息发送时间不能晚于服务器时间 10 分钟以上");
            }
        }

        // Redis SETNX 预检（优化路径，非权威）
        String redisKey = "inbox:" + idempotencyKey;
        if (!tryAcquireRedisLock(redisKey)) {
            ConversationMessageEntity existing = conversationMessageMapper.selectOne(
                    new LambdaQueryWrapper<ConversationMessageEntity>()
                            .eq(ConversationMessageEntity::getIdempotencyKey, idempotencyKey)
                            .orderByAsc(ConversationMessageEntity::getId)
                            .last("LIMIT 1"));
            if (existing != null) {
                String reply = findAssistantReply(idempotencyKey);
                log.info("重复消息已忽略(Redis), idempotencyKey={}, messageId={}", idempotencyKey, existing.getId());
                return new InboundMessageResponse(true, true, existing.getId(), reply);
            }
            // Redis 误判（DB 已提交但缓存过期或尚未写入），继续处理
        }

        // 查找或创建用户和渠道绑定
        UserEntity user = findOrCreateUser(now);
        ChannelBindingEntity binding = findOrCreateBinding(user.getId(), channel, externalUserId, now);

        // 保存 USER 消息（数据库唯一约束是最终幂等保证）
        ConversationMessageEntity userMsg = new ConversationMessageEntity();
        userMsg.setChannelBindingId(binding.getId());
        userMsg.setIdempotencyKey(idempotencyKey);
        userMsg.setExternalMessageId(externalMessageId);
        userMsg.setRole(MessageRole.USER.name());
        userMsg.setContent(text);
        userMsg.setContentHash(hashContent(text));
        userMsg.setDeliveryStatus(DeliveryStatus.CREATED.name());
        if (sentAt != null) {
            userMsg.setSentAt(sentAt.toLocalDateTime());
        }

        int rows = conversationMessageMapper.insertIgnore(userMsg);
        if (rows == 0) {
            // 唯一键冲突 = 重复消息，查询已有记录
            ConversationMessageEntity existing = conversationMessageMapper.selectOne(
                    new LambdaQueryWrapper<ConversationMessageEntity>()
                            .eq(ConversationMessageEntity::getIdempotencyKey, idempotencyKey)
                            .orderByAsc(ConversationMessageEntity::getId)
                            .last("LIMIT 1"));
            if (existing == null) {
                log.error("幂等键冲突但找不到现有记录, idempotencyKey={}", idempotencyKey);
                throw new BizException("消息重复但无法找到原始记录");
            }
            String reply = findAssistantReply(idempotencyKey);
            log.info("重复消息已忽略(ON CONFLICT), idempotencyKey={}, messageId={}", idempotencyKey, existing.getId());
            return new InboundMessageResponse(true, true, existing.getId(), reply);
        }

        // 保存 ASSISTANT 消息，根据渠道决定投递方式
        ConversationMessageEntity assistantMsg = new ConversationMessageEntity();
        assistantMsg.setChannelBindingId(binding.getId());
        assistantMsg.setIdempotencyKey(idempotencyKey);
        assistantMsg.setRole(MessageRole.ASSISTANT.name());
        assistantMsg.setContent(FIXED_REPLY_TEXT);
        assistantMsg.setContentHash(hashContent(FIXED_REPLY_TEXT));

        if (MOCK_CHANNEL.equals(channel)) {
            // Mock 渠道：同步回复，直接标记 SENT（无外部发送副作用）
            assistantMsg.setDeliveryStatus(DeliveryStatus.SENT.name());
            assistantMsg.setSentAt(now);
            conversationMessageMapper.insert(assistantMsg);
        } else {
            // 真实渠道：标记 CREATED，由调用方在事务提交后触发异步发送
            assistantMsg.setDeliveryStatus(DeliveryStatus.CREATED.name());
            conversationMessageMapper.insert(assistantMsg);
        }

        log.info("入站消息处理完成, channel={}, messageId={}, idempotencyKey={}",
                channel, userMsg.getId(), idempotencyKey);

        return new InboundMessageResponse(true, false, userMsg.getId(), FIXED_REPLY_TEXT);
    }

    @Override
    public List<OutboundMessageResponse> listSentReplies(String externalUserId) {
        ChannelBindingEntity binding = channelBindingMapper.selectOne(
                new LambdaQueryWrapper<ChannelBindingEntity>()
                        .eq(ChannelBindingEntity::getChannel, MOCK_CHANNEL)
                        .eq(ChannelBindingEntity::getExternalUserId, externalUserId)
        );
        if (binding == null) {
            return List.of();
        }
        return conversationMessageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getChannelBindingId, binding.getId())
                        .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT.name())
                        .eq(ConversationMessageEntity::getDeliveryStatus, DeliveryStatus.SENT.name())
                        .orderByAsc(ConversationMessageEntity::getCreatedAt)
                        .orderByAsc(ConversationMessageEntity::getId)
        ).stream().map(msg -> new OutboundMessageResponse(
                msg.getId(),
                msg.getContent(),
                msg.getCreatedAt() != null
                        ? msg.getCreatedAt().atOffset(ZoneOffset.ofHours(8))
                        : null
        )).toList();
    }

    // ---- 私有方法 ----

    private String findAssistantReply(String idempotencyKey) {
        ConversationMessageEntity msg = conversationMessageMapper.selectOne(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getIdempotencyKey, idempotencyKey)
                        .eq(ConversationMessageEntity::getRole, MessageRole.ASSISTANT.name())
        );
        return msg != null ? msg.getContent() : "";
    }

    private boolean tryAcquireRedisLock(String key) {
        try {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(3600));
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("Redis 不可用, key={}", key, e);
            return true;
        }
    }

    private UserEntity findOrCreateUser(LocalDateTime now) {
        List<UserEntity> users = userMapper.selectList(null);
        if (!users.isEmpty()) {
            return users.get(0);
        }
        UserEntity user = new UserEntity();
        user.setStatus("ACTIVE");
        user.setTimezone("Asia/Shanghai");
        userMapper.insert(user);
        return user;
    }

    private ChannelBindingEntity findOrCreateBinding(Long userId, String channel,
                                                      String externalUserId, LocalDateTime now) {
        ChannelBindingEntity binding = channelBindingMapper.selectOne(
                new LambdaQueryWrapper<ChannelBindingEntity>()
                        .eq(ChannelBindingEntity::getChannel, channel)
                        .eq(ChannelBindingEntity::getExternalUserId, externalUserId)
        );
        if (binding != null) {
            return binding;
        }
        binding = new ChannelBindingEntity();
        binding.setUserId(userId);
        binding.setChannel(channel);
        binding.setExternalUserId(externalUserId);
        channelBindingMapper.insert(binding);
        return binding;
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
