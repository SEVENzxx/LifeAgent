package com.lifeagent.service;

import com.lifeagent.dto.InboundMessageRequest;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.entity.ConversationMessageEntity;
import com.lifeagent.mapper.ChannelBindingMapper;
import com.lifeagent.mapper.ConversationMessageMapper;
import com.lifeagent.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationService 真实数据库集成测试。
 *
 * <p>连接本地 Compose 的 PostgreSQL + Redis，验证完整入站、
 * 幂等重复和非法请求处理路径。{@code @BeforeEach} 清理测试表确保隔离。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MOCK_CHANNEL_ENABLED=true"})
class ConversationServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private ChannelBindingMapper channelBindingMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void cleanDatabase() {
        conversationMessageMapper.delete(null);
        channelBindingMapper.delete(null);
        userMapper.delete(null);
    }

    @Test
    @DisplayName("首次请求 200，删除 Redis key 后重复仍 200、duplicate=true、messageId 不变、DB 无新增")
    void firstRequestAndDuplicateAfterRedisKeyDeletion() {
        InboundMessageRequest request = new InboundMessageRequest();
        request.setExternalMessageId("mock-msg-001");
        request.setExternalUserId("mock-user-001");
        request.setText("你好");
        request.setSentAt(OffsetDateTime.now());

        // 1) 首次请求
        ResponseEntity<InboundMessageResponse> firstResp = restTemplate.postForEntity(
                "/api/v1/mock/messages/inbound", request, InboundMessageResponse.class);
        assertEquals(HttpStatus.OK, firstResp.getStatusCode());
        InboundMessageResponse firstBody = firstResp.getBody();
        assertNotNull(firstBody);
        assertTrue(firstBody.isAccepted());
        assertFalse(firstBody.isDuplicate());
        Long firstMessageId = firstBody.getMessageId();
        assertNotNull(firstMessageId);
        assertEquals(2, conversationMessageMapper.selectCount(null).intValue());

        // 2) 删除 Redis key，模拟 Redis 预检退化
        stringRedisTemplate.delete("inbox:MOCK:mock-msg-001");

        // 3) 重复请求
        ResponseEntity<InboundMessageResponse> secondResp = restTemplate.postForEntity(
                "/api/v1/mock/messages/inbound", request, InboundMessageResponse.class);
        assertEquals(HttpStatus.OK, secondResp.getStatusCode());
        InboundMessageResponse secondBody = secondResp.getBody();
        assertNotNull(secondBody);
        assertTrue(secondBody.isAccepted());
        assertTrue(secondBody.isDuplicate());
        assertEquals(firstMessageId, secondBody.getMessageId());

        // 4) 数据库没有新增行
        assertEquals(2, conversationMessageMapper.selectCount(null).intValue());
    }

    @Test
    @DisplayName("未知字段返回 HTTP 400，不写数据库")
    void unknownFieldReturns400() {
        String unknownFieldJson = """
                {
                    "externalMessageId": "mock-msg-001",
                    "externalUserId": "mock-user-001",
                    "text": "你好",
                    "sentAt": "2026-07-14T20:00:00+08:00",
                    "unknownField": "should cause 400"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(unknownFieldJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/mock/messages/inbound", HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, conversationMessageMapper.selectCount(null).intValue());
        assertEquals(0, channelBindingMapper.selectCount(null).intValue());
    }

    @Test
    @DisplayName("非法 JSON 返回 HTTP 400，不写数据库")
    void invalidJsonReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("not valid json at all", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/mock/messages/inbound", HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, conversationMessageMapper.selectCount(null).intValue());
    }
}
