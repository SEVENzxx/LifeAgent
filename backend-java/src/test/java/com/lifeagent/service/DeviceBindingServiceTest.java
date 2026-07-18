package com.lifeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lifeagent.common.exception.ForbiddenException;
import com.lifeagent.common.exception.UnauthorizedException;
import com.lifeagent.entity.DeviceBindingEntity;
import com.lifeagent.mapper.DeviceBindingMapper;
import com.lifeagent.service.impl.DeviceBindingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class DeviceBindingServiceTest {

    @Mock
    private DeviceBindingMapper deviceBindingMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<String> cacheValueCaptor;

    @Captor
    private ArgumentCaptor<Long> ttlCaptor;

    @Captor
    private ArgumentCaptor<TimeUnit> timeUnitCaptor;

    private final ObjectMapper objectMapper = new JsonMapper();
    private DeviceBindingService service;

    private static final String DEVICE_ID = "test-iphone";
    private static final String VALID_TOKEN = "valid-token-1234567890";
    private static final String WRONG_TOKEN = "wrong-token";
    private static final String CACHE_KEY = "lifeagent:behavior:device-binding:" + DEVICE_ID;
    private static final String HASH = DeviceBindingService.sha256Hex(VALID_TOKEN);

    private DeviceBindingEntity activeBinding;

    @BeforeEach
    void setUp() {
        service = new DeviceBindingServiceImpl(deviceBindingMapper, stringRedisTemplate, objectMapper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        activeBinding = new DeviceBindingEntity();
        activeBinding.setId(1L);
        activeBinding.setUserId(42L);
        activeBinding.setDeviceId(DEVICE_ID);
        activeBinding.setDisplayName("Test iPhone");
        activeBinding.setSourceType("SHORTCUT");
        activeBinding.setCredentialHash(HASH);
        activeBinding.setStatus("ACTIVE");
        activeBinding.setCreatedAt(Instant.parse("2026-07-17T10:00:00Z"));
        activeBinding.setUpdatedAt(Instant.parse("2026-07-17T10:00:00Z"));
    }

    @Test
    @DisplayName("SHA-256 哈希计算正确")
    void shouldComputeSha256Hex() {
        String hash = DeviceBindingService.sha256Hex("hello");
        assertThat(hash).isEqualTo(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("SHA-256 不同输入产生不同哈希")
    void shouldComputeDifferentHashForDifferentInput() {
        String hash1 = DeviceBindingService.sha256Hex("token-123");
        String hash2 = DeviceBindingService.sha256Hex("token-456");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("SHA-256 相同输入产生相同哈希")
    void shouldComputeDeterministicHash() {
        String hash1 = DeviceBindingService.sha256Hex("my-secret-token");
        String hash2 = DeviceBindingService.sha256Hex("my-secret-token");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("SHA-256 空输入不报错")
    void shouldHandleEmptyInput() {
        String hash = DeviceBindingService.sha256Hex("");
        assertThat(hash).isNotBlank();
        assertThat(hash.length()).isEqualTo(64);
    }

    // ========== 鉴权流程 ==========

    @Test
    @DisplayName("缺少设备 ID 返回 401")
    void missingDeviceIdThrowsUnauthorized() {
        assertThatThrownBy(() -> service.authenticate(null, VALID_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("缺少设备标识");
        assertThatThrownBy(() -> service.authenticate("  ", VALID_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("缺少设备标识");
    }

    @Test
    @DisplayName("缺少 Token 返回 401")
    void missingTokenThrowsUnauthorized() {
        assertThatThrownBy(() -> service.authenticate(DEVICE_ID, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("缺少设备凭证");
        assertThatThrownBy(() -> service.authenticate(DEVICE_ID, "  "))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("缺少设备凭证");
    }

    @Test
    @DisplayName("缓存命中时直接验证不查询 SQL")
    void cacheHitSkipsDatabase() throws Exception {
        String cacheJson = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("bindingId", 1L)
                        .put("userId", 42L)
                        .put("credentialHash", HASH)
                        .put("status", "ACTIVE")
                        .put("sourceType", "SHORTCUT"));
        when(valueOperations.get(CACHE_KEY)).thenReturn(cacheJson);

        DeviceBindingEntity result = service.authenticate(DEVICE_ID, VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(42L);
        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(deviceBindingMapper, never()).selectByDeviceId(any());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("缓存未命中时回源 SQL 并回填缓存")
    void cacheMissFallsBackToDatabase() throws Exception {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        DeviceBindingEntity result = service.authenticate(DEVICE_ID, VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(42L);

        verify(valueOperations).set(eq(CACHE_KEY), cacheValueCaptor.capture(),
                eq(86400L), eq(TimeUnit.SECONDS));
        String cachedJson = cacheValueCaptor.getValue();
        assertThat(cachedJson).contains("\"bindingId\":1");
        assertThat(cachedJson).contains("\"userId\":42");
        assertThat(cachedJson).contains("\"credentialHash\"");
        assertThat(cachedJson).contains("\"status\":\"ACTIVE\"");
        assertThat(cachedJson).contains("\"sourceType\":\"SHORTCUT\"");
        assertThat(cachedJson).doesNotContain(VALID_TOKEN);
        assertThat(cachedJson).doesNotContain("displayName");
    }

    @Test
    @DisplayName("Redis 不可用时降级到 SQL，合法上传不受影响")
    void redisFailureFallsBackToDatabase() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis 连接超时"));
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        DeviceBindingEntity result = service.authenticate(DEVICE_ID, VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("缓存值损坏时降级到 SQL")
    void corruptCacheFallsBackToDatabase() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("invalid-json");
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        DeviceBindingEntity result = service.authenticate(DEVICE_ID, VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("缓存命中不足字段时降级到 SQL")
    void cacheMissingFieldsFallsBackToDatabase() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("{\"bindingId\":1,\"userId\":42}");
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        DeviceBindingEntity result = service.authenticate(DEVICE_ID, VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("数据库不存在设备返回 403 且不做负缓存")
    void deviceNotFoundReturnsForbiddenNoNegativeCache() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate(DEVICE_ID, VALID_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("设备凭证错误");

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("错误 Token 返回 403 且不写事件")
    void wrongTokenReturnsForbidden() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        assertThatThrownBy(() -> service.authenticate(DEVICE_ID, WRONG_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("设备凭证错误");

        verify(deviceBindingMapper, never()).insert(any(DeviceBindingEntity.class));
        verify(deviceBindingMapper, never()).updateCredentialHash(any(), any(), any());
    }

    @Test
    @DisplayName("缓存中错误 Token 也返回 403")
    void wrongTokenWithCacheHitReturnsForbidden() throws Exception {
        String cacheJson = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("bindingId", 1L)
                        .put("userId", 42L)
                        .put("credentialHash", HASH)
                        .put("status", "ACTIVE")
                        .put("sourceType", "SHORTCUT"));
        when(valueOperations.get(CACHE_KEY)).thenReturn(cacheJson);

        assertThatThrownBy(() -> service.authenticate(DEVICE_ID, WRONG_TOKEN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("设备凭证错误");

        verify(deviceBindingMapper, never()).selectByDeviceId(any());
    }

    @Test
    @DisplayName("REVOKED 绑定从缓存返回 403 且不写事件")
    void revokedBindingReturnsForbidden() throws Exception {
        String revokedToken = "revoked-token";
        String revokedHash = DeviceBindingService.sha256Hex(revokedToken);
        String revokedKey = "lifeagent:behavior:device-binding:revoked-device";
        String cacheJson = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("bindingId", 2L)
                        .put("userId", 42L)
                        .put("credentialHash", revokedHash)
                        .put("status", "REVOKED")
                        .put("sourceType", "SHORTCUT"));
        when(valueOperations.get(revokedKey)).thenReturn(cacheJson);

        assertThatThrownBy(() -> service.authenticate("revoked-device", revokedToken))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("设备已被撤销");

        verify(deviceBindingMapper, never()).selectByDeviceId(any());
        verify(deviceBindingMapper, never()).insert(any(DeviceBindingEntity.class));
    }

    @Test
    @DisplayName("SQL 查询的 REVOKED 绑定同样返回 403")
    void revokedBindingFromSqlReturnsForbidden() {
        String revokedToken = "revoked-token";
        String revokedHash = DeviceBindingService.sha256Hex(revokedToken);
        activeBinding.setCredentialHash(revokedHash);
        activeBinding.setStatus("REVOKED");

        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        assertThatThrownBy(() -> service.authenticate(DEVICE_ID, revokedToken))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("设备已被撤销");

        verify(valueOperations).set(anyString(), anyString(), eq(86400L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("缓存 TTL 固定为 86400 秒且不续期")
    void cacheTtlIsFixed() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(deviceBindingMapper.selectByDeviceId(DEVICE_ID)).thenReturn(activeBinding);

        service.authenticate(DEVICE_ID, VALID_TOKEN);

        verify(valueOperations).set(anyString(), anyString(), ttlCaptor.capture(), timeUnitCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(86400L);
        assertThat(timeUnitCaptor.getValue()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("多次鉴权缓存命中不续期（只读缓存不写）")
    void cacheHitDoesNotRefreshTtl() throws Exception {
        String cacheJson = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("bindingId", 1L)
                        .put("userId", 42L)
                        .put("credentialHash", HASH)
                        .put("status", "ACTIVE")
                        .put("sourceType", "SHORTCUT"));
        when(valueOperations.get(CACHE_KEY)).thenReturn(cacheJson);

        service.authenticate(DEVICE_ID, VALID_TOKEN);
        service.authenticate(DEVICE_ID, VALID_TOKEN);

        verify(valueOperations, times(2)).get(CACHE_KEY);
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }
}
