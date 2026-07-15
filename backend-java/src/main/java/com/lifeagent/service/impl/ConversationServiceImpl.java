package com.lifeagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lifeagent.common.BizException;
import com.lifeagent.dto.InboundMessageCommand;
import com.lifeagent.dto.InboundMessageResponse;
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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 会话服务实现。
 *
 * <p>同一事务保存 USER 消息（CREATED）后返回 userId/bindingId，
 * 由调用方在事务提交后触发异步 AI 处理。
 * 幂等由 idempotency_key 数据库唯一约束保证。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final UserMapper userMapper;
    private final ChannelBindingMapper channelBindingMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

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
                log.info("重复消息已忽略(Redis), idempotencyKey={}, messageId={}", idempotencyKey, existing.getId());
                return InboundMessageResponse.builder()
                        .accepted(true).duplicate(true).messageId(existing.getId()).build();
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
            log.info("重复消息已忽略(ON CONFLICT), idempotencyKey={}, messageId={}", idempotencyKey, existing.getId());
            return InboundMessageResponse.builder()
                    .accepted(true).duplicate(true).messageId(existing.getId()).build();
        }

        // 由调用方异步创建 ASSISTANT 并发送
        log.info("入站消息处理完成, channel={}, messageId={}, idempotencyKey={}",
                channel, userMsg.getId(), idempotencyKey);
        return InboundMessageResponse.builder()
                .accepted(true).duplicate(false).messageId(userMsg.getId())
                .userId(user.getId()).bindingId(binding.getId()).build();
    }

    // ---- 私有方法 ----

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
