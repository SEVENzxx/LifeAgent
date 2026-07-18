package com.lifeagent.controller;

import com.lifeagent.common.DatabaseNameValidator;
import com.lifeagent.common.ApiResponse;
import com.lifeagent.dto.behavior.ActivityWindowFacts;
import com.lifeagent.entity.DeviceBindingEntity;
import com.lifeagent.entity.MonitoredAppEntity;
import com.lifeagent.entity.UserEntity;
import com.lifeagent.mapper.ActivityIntervalMapper;
import com.lifeagent.mapper.BehaviorEventMapper;
import com.lifeagent.mapper.DeviceBindingMapper;
import com.lifeagent.mapper.MonitoredAppMapper;
import com.lifeagent.mapper.UserMapper;
import com.lifeagent.service.DeviceBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行为事件上传与窗口查询集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BehaviorEventIntegrationTest {

    private static final String TEST_DEVICE_ID = "test-iphone";
    private static final String TEST_TOKEN = "test-device-token-1234567890";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeviceBindingMapper deviceBindingMapper;

    @Autowired
    private MonitoredAppMapper monitoredAppMapper;

    @Autowired
    private BehaviorEventMapper behaviorEventMapper;

    @Autowired
    private ActivityIntervalMapper activityIntervalMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DataSource dataSource;

    private static final String REDIS_CACHE_KEY =
            "lifeagent:behavior:device-binding:" + TEST_DEVICE_ID;

    private Long userId;

    @BeforeEach
    void setUp() {
        DatabaseNameValidator.requireTestDatabase(dataSource);

        activityIntervalMapper.delete(null);
        behaviorEventMapper.delete(null);
        monitoredAppMapper.delete(null);
        deviceBindingMapper.delete(null);
        userMapper.delete(null);

        try {
            stringRedisTemplate.delete(REDIS_CACHE_KEY);
        } catch (Exception e) {
        }

        UserEntity user = new UserEntity();
        user.setStatus("ACTIVE");
        user.setTimezone("Asia/Shanghai");
        user.setCreatedAt(LocalDateTime.of(2026, 7, 17, 18, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 7, 17, 18, 0));
        userMapper.insert(user);
        userId = user.getId();

        String hash = DeviceBindingService.sha256Hex(TEST_TOKEN);
        DeviceBindingEntity binding = new DeviceBindingEntity();
        binding.setUserId(userId);
        binding.setDeviceId(TEST_DEVICE_ID);
        binding.setDisplayName("Test iPhone");
        binding.setSourceType("SHORTCUT");
        binding.setCredentialHash(hash);
        binding.setStatus("ACTIVE");
        binding.setCreatedAt(Instant.parse("2026-07-17T10:00:00Z"));
        binding.setUpdatedAt(Instant.parse("2026-07-17T10:00:00Z"));
        deviceBindingMapper.insert(binding);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", TEST_DEVICE_ID);
        headers.set("X-Device-Token", TEST_TOKEN);
        return headers;
    }

    private ResponseEntity<ApiResponse<Map>> uploadEvent(Map<String, Object> body) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, authHeaders());
        return restTemplate.exchange(
                "/api/v1/behavior-events",
                HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<Map>>() {});
    }

    @Test
    @DisplayName("无 Token 返回 401")
    void noTokenReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", TEST_DEVICE_ID);
        Map<String, Object> body = Map.of(
                "eventId", "00000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<ApiResponse<Map>> response = restTemplate.exchange(
                "/api/v1/behavior-events", HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<Map>>() {});
        assertThat(response.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    @DisplayName("错误 Token 返回 403")
    void wrongTokenReturns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", TEST_DEVICE_ID);
        headers.set("X-Device-Token", "wrong-token");
        Map<String, Object> body = Map.of(
                "eventId", "00000000-0000-0000-0000-000000000002",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<ApiResponse<Map>> response = restTemplate.exchange(
                "/api/v1/behavior-events", HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<Map>>() {});
        assertThat(response.getStatusCodeValue()).isEqualTo(403);
    }

    @Test
    @DisplayName("OPEN(A)→CLOSE(A) 得到一个 EXACT 区间")
    void openThenCloseCreatesExactInterval() {
        ResponseEntity<ApiResponse<Map>> openResp = uploadEvent(Map.of(
                "eventId", "10000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        assertThat(openResp.getStatusCodeValue()).isEqualTo(200);

        ResponseEntity<ApiResponse<Map>> closeResp = uploadEvent(Map.of(
                "eventId", "10000000-0000-0000-0000-000000000002",
                "appKey", "wechat", "appName", "微信",
                "eventType", "CLOSE", "eventTime", "2026-07-17T18:30:00+08:00",
                "clientVersion", "shortcut-v1"));
        assertThat(closeResp.getStatusCodeValue()).isEqualTo(200);

        assertThat(behaviorEventMapper.selectCount(null)).isEqualTo(2);
        assertThat(activityIntervalMapper.selectCount(null)).isEqualTo(1);
        var intervals = activityIntervalMapper.selectList(null);
        assertThat(intervals.get(0).getQuality()).isEqualTo("EXACT");
        assertThat(intervals.get(0).getEndReason()).isEqualTo("EXPLICIT_CLOSE");
    }

    @Test
    @DisplayName("相同事件重复上传返回 DUPLICATE")
    void duplicateEventReturnsDuplicate() {
        Map<String, Object> body = Map.of(
                "eventId", "20000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1");

        ResponseEntity<ApiResponse<Map>> first = uploadEvent(body);
        assertThat(first.getStatusCodeValue()).isEqualTo(200);

        ResponseEntity<ApiResponse<Map>> second = uploadEvent(body);
        assertThat(second.getStatusCodeValue()).isEqualTo(200);

        assertThat(behaviorEventMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    @DisplayName("相同 ID 不同正文返回 409")
    void sameIdDifferentContentReturns409() {
        Map<String, Object> body1 = Map.of(
                "eventId", "30000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1");
        ResponseEntity<ApiResponse<Map>> first = uploadEvent(body1);
        assertThat(first.getStatusCodeValue()).isEqualTo(200);

        Map<String, Object> body2 = Map.of(
                "eventId", "30000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "CLOSE", "eventTime", "2026-07-17T18:30:00+08:00",
                "clientVersion", "shortcut-v1");
        ResponseEntity<ApiResponse<Map>> second = uploadEvent(body2);
        assertThat(second.getStatusCodeValue()).isEqualTo(409);

        assertThat(behaviorEventMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    @DisplayName("非法 eventType 被拒绝")
    void invalidEventTypeRejected() {
        Map<String, Object> body = Map.of(
                "eventId", "40000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "INVALID", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1");
        ResponseEntity<ApiResponse<Map>> response = uploadEvent(body);
        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    @Test
    @DisplayName("首次事件自动登记 App")
    void firstEventCreatesApp() {
        uploadEvent(Map.of(
                "eventId", "50000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));

        List<MonitoredAppEntity> apps = monitoredAppMapper.selectByUserId(userId);
        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).getAppKey()).isEqualTo("wechat");
        assertThat(apps.get(0).getDisplayName()).isEqualTo("微信");
    }

    @Test
    @DisplayName("OPEN(A)→OPEN(B) 推断关闭 A")
    void openASwitchToB() {
        uploadEvent(Map.of(
                "eventId", "60000000-0000-0000-0000-000000000001",
                "appKey", "appa", "appName", "App A",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        uploadEvent(Map.of(
                "eventId", "60000000-0000-0000-0000-000000000002",
                "appKey", "appb", "appName", "App B",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:30:00+08:00",
                "clientVersion", "shortcut-v1"));

        assertThat(activityIntervalMapper.selectCount(null)).isEqualTo(2);
        var intervals = activityIntervalMapper.selectList(null);
        var aInterval = intervals.stream().filter(i -> i.getEndAt() != null).findFirst();
        var bInterval = intervals.stream().filter(i -> i.getEndAt() == null).findFirst();
        assertThat(aInterval).isPresent();
        assertThat(bInterval).isPresent();
        assertThat(aInterval.get().getQuality()).isEqualTo("INFERRED_SWITCH");
        assertThat(aInterval.get().getEndReason()).isEqualTo("APP_SWITCH");
    }

    @Test
    @DisplayName("重复 OPEN 不创建重叠区间")
    void redundantOpenDoesNotCreateDuplicateInterval() {
        uploadEvent(Map.of(
                "eventId", "70000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        uploadEvent(Map.of(
                "eventId", "70000000-0000-0000-0000-000000000002",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:05:00+08:00",
                "clientVersion", "shortcut-v1"));

        assertThat(activityIntervalMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    @DisplayName("孤立 CLOSE 不创建区间")
    void orphanCloseDoesNotCreateInterval() {
        uploadEvent(Map.of(
                "eventId", "80000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "CLOSE", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));

        assertThat(activityIntervalMapper.selectCount(null)).isEqualTo(0);
        assertThat(behaviorEventMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    @DisplayName("窗口查询返回正确聚合")
    void windowQueryReturnsAggregation() {
        uploadEvent(Map.of(
                "eventId", "90000000-0000-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        uploadEvent(Map.of(
                "eventId", "90000000-0000-0000-0000-000000000002",
                "appKey", "wechat", "appName", "微信",
                "eventType", "CLOSE", "eventTime", "2026-07-17T18:30:00+08:00",
                "clientVersion", "shortcut-v1"));

        HttpHeaders headers = authHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<ActivityWindowFacts>> response = restTemplate.exchange(
                "/api/v1/behavior/activity-window?from={from}&to={to}",
                HttpMethod.GET, request,
                new ParameterizedTypeReference<ApiResponse<ActivityWindowFacts>>() {},
                "2026-07-17T17:00:00+08:00",
                "2026-07-17T20:00:00+08:00");
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        ActivityWindowFacts facts = response.getBody().getData();
        assertThat(facts).isNotNull();
        assertThat(facts.getQuality()).isEqualTo("SUFFICIENT");
        assertThat(facts.getDuration().getExactSeconds()).isEqualTo(1800);
        assertThat(facts.getMonitoredApps()).isNotEmpty();
    }

    @Test
    @DisplayName("无事件窗口返回 INSUFFICIENT")
    void emptyWindowReturnsInsufficient() {
        HttpHeaders headers = authHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<ApiResponse<ActivityWindowFacts>> response = restTemplate.exchange(
                "/api/v1/behavior/activity-window?from={from}&to={to}",
                HttpMethod.GET, request,
                new ParameterizedTypeReference<ApiResponse<ActivityWindowFacts>>() {},
                "2026-07-17T17:00:00+08:00",
                "2026-07-17T20:00:00+08:00");
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        ActivityWindowFacts facts = response.getBody().getData();
        assertThat(facts.getQuality()).isEqualTo("INSUFFICIENT");
        assertThat(facts.getDuration().getExactSeconds()).isEqualTo(0);
    }

    // ========== Redis 缓存集成测试 ==========

    @Test
    @DisplayName("首次上传后缓存被填充，缓存包含最小字段不含 displayName")
    void cachePopulatedAfterFirstUpload() {
        ResponseEntity<ApiResponse<Map>> first = uploadEvent(Map.of(
                "eventId", "cache-0001-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        assertThat(first.getStatusCodeValue()).isEqualTo(200);

        String cached = stringRedisTemplate.opsForValue().get(REDIS_CACHE_KEY);
        assertThat(cached).isNotNull();
        assertThat(cached).contains("\"bindingId\"");
        assertThat(cached).contains("\"userId\"");
        assertThat(cached).contains("\"credentialHash\"");
        assertThat(cached).contains("\"status\":\"ACTIVE\"");
        assertThat(cached).doesNotContain("displayName");

        Long ttl = stringRedisTemplate.getExpire(REDIS_CACHE_KEY);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(86400);
    }

    @Test
    @DisplayName("Redis 缓存被清除后自动重新从 SQL 读取并回填")
    void cacheDeletedRefillsFromSql() {
        uploadEvent(Map.of(
                "eventId", "cache-0002-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        assertThat(stringRedisTemplate.hasKey(REDIS_CACHE_KEY)).isTrue();

        stringRedisTemplate.delete(REDIS_CACHE_KEY);
        assertThat(stringRedisTemplate.hasKey(REDIS_CACHE_KEY)).isFalse();

        ResponseEntity<ApiResponse<Map>> retry = uploadEvent(Map.of(
                "eventId", "cache-0002-0000-0000-000000000002",
                "appKey", "wechat", "appName", "微信",
                "eventType", "CLOSE", "eventTime", "2026-07-17T18:30:00+08:00",
                "clientVersion", "shortcut-v1"));
        assertThat(retry.getStatusCodeValue()).isEqualTo(200);

        assertThat(stringRedisTemplate.hasKey(REDIS_CACHE_KEY)).isTrue();
        String cached = stringRedisTemplate.opsForValue().get(REDIS_CACHE_KEY);
        assertThat(cached).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    @DisplayName("数据库撤销后删除缓存，新状态立即生效")
    void revokeDeviceAfterCacheDeleteTakesEffect() {
        uploadEvent(Map.of(
                "eventId", "cache-0003-0000-0000-000000000001",
                "appKey", "wechat", "appName", "微信",
                "eventType", "OPEN", "eventTime", "2026-07-17T18:00:00+08:00",
                "clientVersion", "shortcut-v1"));
        assertThat(stringRedisTemplate.hasKey(REDIS_CACHE_KEY)).isTrue();

        DeviceBindingEntity binding = deviceBindingMapper.selectByDeviceId(TEST_DEVICE_ID);
        binding.setStatus("REVOKED");
        deviceBindingMapper.updateById(binding);
        stringRedisTemplate.delete(REDIS_CACHE_KEY);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Device-Id", TEST_DEVICE_ID);
        headers.set("X-Device-Token", TEST_TOKEN);
        Map<String, Object> body = Map.of(
                "eventId", "cache-0003-0000-0000-000000000002",
                "appKey", "wechat", "appName", "微信",
                "eventType", "CLOSE", "eventTime", "2026-07-17T18:30:00+08:00",
                "clientVersion", "shortcut-v1");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<ApiResponse<Map>> response = restTemplate.exchange(
                "/api/v1/behavior-events", HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<Map>>() {});
        assertThat(response.getStatusCodeValue()).isEqualTo(403);

        binding.setStatus("ACTIVE");
        deviceBindingMapper.updateById(binding);
    }
}
