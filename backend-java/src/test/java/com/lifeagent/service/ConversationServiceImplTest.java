package com.lifeagent.service;

import com.lifeagent.common.BizException;
import com.lifeagent.dto.InboundMessageRequest;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.dto.OutboundMessageResponse;
import com.lifeagent.entity.ChannelBindingEntity;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.entity.UserEntity;
import com.lifeagent.enums.MessageRole;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.mapper.UserMapper;
import com.lifeagent.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConversationService 单元测试（最终三表架构）。
 *
 * <p>覆盖幂等 UK 约束、Redis 预检、Redis 不可用、
 * 非法请求、出站查询等核心流程。</p>
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    private ConversationService conversationService;

    @Mock
    private UserMapper userMapper;
    @Mock
    private ChannelBindingMapper channelBindingMapper;
    @Mock
    private ConversationMessageMapper conversationMessageMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<ConversationMessageEntity> insertIgnoreCaptor;

    @Captor
    private ArgumentCaptor<ConversationMessageEntity> messageCaptor;

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-07-14T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(
                userMapper, channelBindingMapper, conversationMessageMapper,
                stringRedisTemplate, fixedClock);
    }

    @Test
    @DisplayName("首次入站：创建用户、绑定、保存 USER+ASSISTANT(SENT)、返回 200")
    void firstInboundCreatesFullChain() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(userMapper.selectList(null)).thenReturn(List.of());
        when(channelBindingMapper.selectOne(any())).thenReturn(null);

        doAnswer(invocation -> { ((UserEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(userMapper).insert(any(UserEntity.class));
        doAnswer(invocation -> { ((ChannelBindingEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(channelBindingMapper).insert(any(ChannelBindingEntity.class));
        doAnswer(invocation -> { ((ConversationMessageEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(conversationMessageMapper).insertIgnore(any(ConversationMessageEntity.class));
        doAnswer(invocation -> { ((ConversationMessageEntity) invocation.getArgument(0)).setId(2L); return 1; })
                .when(conversationMessageMapper).insert(any(ConversationMessageEntity.class));

        InboundMessageRequest request = createRequest("mock-msg-001", "mock-user-001", "你好");
        InboundMessageResponse response = conversationService.receiveInboundMessage(request);

        assertTrue(response.isAccepted());
        assertFalse(response.isDuplicate());
        assertEquals(1L, response.getMessageId());
        assertEquals("收到，我已经记录这条消息。", response.getReply());

        verify(conversationMessageMapper).insertIgnore(insertIgnoreCaptor.capture());
        ConversationMessageEntity userMsg = insertIgnoreCaptor.getValue();
        assertEquals(MessageRole.USER.name(), userMsg.getRole());
        assertEquals("你好", userMsg.getContent());
        assertEquals("MOCK:mock-msg-001", userMsg.getIdempotencyKey());
        assertEquals(1L, userMsg.getChannelBindingId());

        verify(conversationMessageMapper).insert(messageCaptor.capture());
        ConversationMessageEntity assistantMsg = messageCaptor.getValue();
        assertEquals(MessageRole.ASSISTANT.name(), assistantMsg.getRole());
        assertEquals("收到，我已经记录这条消息。", assistantMsg.getContent());
        assertEquals("MOCK:mock-msg-001", assistantMsg.getIdempotencyKey());
        assertEquals("SENT", assistantMsg.getDeliveryStatus());
    }

    @Test
    @DisplayName("ON CONFLICT 检测到重复：insertIgnore 返回 0，查询已有记录返回")
    void duplicateDetectedByOnConflict() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(userMapper.selectList(null)).thenReturn(List.of(createUser(1L)));
        when(channelBindingMapper.selectOne(any())).thenReturn(createBinding(1L, 1L));

        when(conversationMessageMapper.insertIgnore(any(ConversationMessageEntity.class))).thenReturn(0);

        ConversationMessageEntity existingUserMsg = new ConversationMessageEntity();
        existingUserMsg.setId(100L);
        existingUserMsg.setChannelBindingId(1L);
        existingUserMsg.setIdempotencyKey("MOCK:mock-msg-001");
        existingUserMsg.setRole(MessageRole.USER.name());
        existingUserMsg.setContent("你好");

        ConversationMessageEntity assistantMsg = new ConversationMessageEntity();
        assistantMsg.setId(101L);
        assistantMsg.setIdempotencyKey("MOCK:mock-msg-001");
        assistantMsg.setRole(MessageRole.ASSISTANT.name());
        assistantMsg.setContent("收到，我已经记录这条消息。");
        when(conversationMessageMapper.selectOne(any()))
                .thenReturn(existingUserMsg)
                .thenReturn(assistantMsg);

        InboundMessageResponse response = conversationService.receiveInboundMessage(
                createRequest("mock-msg-001", "mock-user-001", "你好"));

        assertTrue(response.isAccepted());
        assertTrue(response.isDuplicate());
        assertEquals(100L, response.getMessageId());
        assertEquals("收到，我已经记录这条消息。", response.getReply());

        verify(conversationMessageMapper, times(1)).insertIgnore(any(ConversationMessageEntity.class));
        verify(conversationMessageMapper, never()).insert(any(ConversationMessageEntity.class));
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    @DisplayName("Redis 预检查到重复：SETNX 返回 false，DB 存在则快速返回")
    void duplicateDetectedByRedisPrecheck() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        ConversationMessageEntity existingUserMsg = new ConversationMessageEntity();
        existingUserMsg.setId(100L);
        existingUserMsg.setChannelBindingId(1L);
        existingUserMsg.setIdempotencyKey("MOCK:mock-msg-001");
        existingUserMsg.setRole(MessageRole.USER.name());
        existingUserMsg.setContent("你好");

        ConversationMessageEntity assistantMsg = new ConversationMessageEntity();
        assistantMsg.setId(101L);
        assistantMsg.setIdempotencyKey("MOCK:mock-msg-001");
        assistantMsg.setRole(MessageRole.ASSISTANT.name());
        assistantMsg.setContent("收到，我已经记录这条消息。");
        when(conversationMessageMapper.selectOne(any()))
                .thenReturn(existingUserMsg)
                .thenReturn(assistantMsg);

        InboundMessageResponse response = conversationService.receiveInboundMessage(
                createRequest("mock-msg-001", "mock-user-001", "你好"));

        assertTrue(response.isAccepted());
        assertTrue(response.isDuplicate());
        assertEquals(100L, response.getMessageId());
        assertEquals("收到，我已经记录这条消息。", response.getReply());

        verify(conversationMessageMapper, never()).insert(any(ConversationMessageEntity.class));
    }

    @Test
    @DisplayName("Redis 误判：SETNX 返回 false 但 DB 无记录，继续正常处理")
    void redisFalsePositiveContinuesProcessing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(userMapper.selectList(null)).thenReturn(List.of());
        when(channelBindingMapper.selectOne(any())).thenReturn(null);

        doAnswer(invocation -> { ((UserEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(userMapper).insert(any(UserEntity.class));
        doAnswer(invocation -> { ((ChannelBindingEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(channelBindingMapper).insert(any(ChannelBindingEntity.class));
        doAnswer(invocation -> { ((ConversationMessageEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(conversationMessageMapper).insertIgnore(any(ConversationMessageEntity.class));
        doAnswer(invocation -> { ((ConversationMessageEntity) invocation.getArgument(0)).setId(2L); return 1; })
                .when(conversationMessageMapper).insert(any(ConversationMessageEntity.class));

        InboundMessageResponse response = conversationService.receiveInboundMessage(
                createRequest("mock-msg-001", "mock-user-001", "你好"));

        assertTrue(response.isAccepted());
        assertFalse(response.isDuplicate());
        assertEquals("收到，我已经记录这条消息。", response.getReply());

        verify(conversationMessageMapper, times(1)).insertIgnore(any(ConversationMessageEntity.class));
        verify(conversationMessageMapper, times(1)).insert(any(ConversationMessageEntity.class));
    }

    @Test
    @DisplayName("Redis 不可用时回退：异常被捕获，正常处理")
    void redisUnavailableFallbackProceeds() {
        when(stringRedisTemplate.opsForValue())
                .thenThrow(new RuntimeException("Redis connection refused"));
        when(userMapper.selectList(null)).thenReturn(List.of());
        when(channelBindingMapper.selectOne(any())).thenReturn(null);

        doAnswer(invocation -> { ((UserEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(userMapper).insert(any(UserEntity.class));
        doAnswer(invocation -> { ((ChannelBindingEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(channelBindingMapper).insert(any(ChannelBindingEntity.class));
        doAnswer(invocation -> { ((ConversationMessageEntity) invocation.getArgument(0)).setId(1L); return 1; })
                .when(conversationMessageMapper).insertIgnore(any(ConversationMessageEntity.class));
        doAnswer(invocation -> { ((ConversationMessageEntity) invocation.getArgument(0)).setId(2L); return 1; })
                .when(conversationMessageMapper).insert(any(ConversationMessageEntity.class));

        InboundMessageResponse response = conversationService.receiveInboundMessage(
                createRequest("mock-msg-001", "mock-user-001", "你好"));

        assertTrue(response.isAccepted());
        assertFalse(response.isDuplicate());
        assertEquals("收到，我已经记录这条消息。", response.getReply());
        verify(conversationMessageMapper, times(1)).insertIgnore(any(ConversationMessageEntity.class));
        verify(conversationMessageMapper, times(1)).insert(any(ConversationMessageEntity.class));
    }

    @Test
    @DisplayName("sentAt 超过服务器 10 分钟时拒绝，不写任何库")
    void sentAtTooFarInFutureRejected() {
        InboundMessageRequest request = createRequest("mock-msg-002", "mock-user-001", "你好");
        request.setSentAt(OffsetDateTime.now(fixedClock).plusHours(1));

        assertThrows(BizException.class,
                () -> conversationService.receiveInboundMessage(request));

        verifyNoInteractions(stringRedisTemplate, userMapper, channelBindingMapper,
                conversationMessageMapper);
    }

    @Test
    @DisplayName("出站查询：按 created_at 和 id 升序返回已发送消息")
    void listSentRepliesReturnsSentMessages() {
        when(channelBindingMapper.selectOne(any())).thenReturn(createBinding(1L, 1L));

        ConversationMessageEntity msg1 = new ConversationMessageEntity();
        msg1.setId(1L);
        msg1.setContent("收到，我已经记录这条消息。");
        msg1.setCreatedAt(LocalDateTime.now(fixedClock));
        ConversationMessageEntity msg2 = new ConversationMessageEntity();
        msg2.setId(2L);
        msg2.setContent("第二条回复");
        msg2.setCreatedAt(LocalDateTime.now(fixedClock).plusSeconds(1));
        when(conversationMessageMapper.selectList(any()))
                .thenReturn(List.of(msg1, msg2));

        List<OutboundMessageResponse> replies = conversationService.listSentReplies("user-alpha");

        assertEquals(2, replies.size());
        assertEquals("收到，我已经记录这条消息。", replies.get(0).getText());
        assertEquals("第二条回复", replies.get(1).getText());
    }

    @Test
    @DisplayName("出站查询：不存在的用户返回空列表")
    void listSentRepliesNoBindingReturnsEmpty() {
        when(channelBindingMapper.selectOne(any())).thenReturn(null);
        List<OutboundMessageResponse> replies = conversationService.listSentReplies("nonexistent");
        assertTrue(replies.isEmpty());
    }

    // ---- 辅助方法 ----

    private InboundMessageRequest createRequest(String msgId, String userId, String text) {
        InboundMessageRequest request = new InboundMessageRequest();
        request.setExternalMessageId(msgId);
        request.setExternalUserId(userId);
        request.setText(text);
        request.setSentAt(OffsetDateTime.now(fixedClock));
        return request;
    }

    private UserEntity createUser(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setStatus("ACTIVE");
        user.setTimezone("Asia/Shanghai");
        return user;
    }

    private ChannelBindingEntity createBinding(Long id, Long userId) {
        ChannelBindingEntity binding = new ChannelBindingEntity();
        binding.setId(id);
        binding.setUserId(userId);
        binding.setChannel("MOCK");
        binding.setExternalUserId("user-alpha");
        return binding;
    }
}
