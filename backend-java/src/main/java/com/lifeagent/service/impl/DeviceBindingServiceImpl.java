package com.lifeagent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifeagent.common.exception.ForbiddenException;
import com.lifeagent.common.exception.UnauthorizedException;
import com.lifeagent.entity.DeviceBindingEntity;
import com.lifeagent.mapper.DeviceBindingMapper;
import com.lifeagent.service.DeviceBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import static com.lifeagent.common.Constants.REDIS_BEHAVIOR_DEVICE_BINDING_PREFIX;

/**
 * 设备凭证校验与绑定服务实现。
 *
 * <p>使用 Redis Cache-Aside 缓存设备绑定，TTL 固定 24 小时（86400 秒），命中不续期。
 * Redis 未命中、值损坏或不可用时回源 PostgreSQL，写入缓存后再返回。
 * 数据库中不存在的设备不做负缓存，保证新增绑定后下一次请求立即可见。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceBindingServiceImpl implements DeviceBindingService {

    private final DeviceBindingMapper deviceBindingMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 设备绑定缓存 TTL（秒）：24 小时 */
    private static final long CACHE_TTL_SECONDS = 86400;

    /**
     * 校验设备 ID 和 Token，返回已激活的设备绑定实体。
     */
    @Override
    public DeviceBindingEntity authenticate(String deviceId, String token) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new UnauthorizedException("缺少设备标识");
        }
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("缺少设备凭证");
        }

        String deviceIdTrimmed = deviceId.trim();

        // 1. 尝试从 Redis 缓存读取
        DeviceBindingEntity binding = getFromCache(deviceIdTrimmed);

        // 2. 缓存未命中或无效，回源 PostgreSQL
        if (binding == null) {
            binding = getFromDatabase(deviceIdTrimmed);
        }

        // 3. 常量时间比较 Token 哈希
        String tokenHash = DeviceBindingService.sha256Hex(token);
        if (!MessageDigest.isEqual(tokenHash.getBytes(StandardCharsets.UTF_8),
                binding.getCredentialHash().getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("设备凭证错误");
        }

        // 4. 检查撤销状态
        if ("REVOKED".equals(binding.getStatus())) {
            throw new ForbiddenException("设备已被撤销");
        }

        return binding;
    }

    /**
     * 从 Redis 读取设备绑定缓存。
     */
    private DeviceBindingEntity getFromCache(String deviceId) {
        String key = buildCacheKey(deviceId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(cached);
            if (!isValidCacheEntry(root)) {
                log.warn("设备绑定缓存格式异常, deviceId={}", maskDeviceId(deviceId));
                return null;
            }
            DeviceBindingEntity entity = new DeviceBindingEntity();
            entity.setId(root.get("bindingId").asLong());
            entity.setUserId(root.get("userId").asLong());
            entity.setDeviceId(deviceId);
            entity.setCredentialHash(root.get("credentialHash").asText());
            entity.setStatus(root.get("status").asText());
            entity.setSourceType(root.get("sourceType").asText());
            log.debug("设备绑定缓存命中, deviceId={}", maskDeviceId(deviceId));
            return entity;
        } catch (Exception e) {
            log.warn("Redis 不可用或缓存读取失败, deviceId={}", maskDeviceId(deviceId));
            return null;
        }
    }

    /**
     * 从 PostgreSQL 查询设备绑定，查询成功后回填 Redis 缓存。
     */
    private DeviceBindingEntity getFromDatabase(String deviceId) {
        DeviceBindingEntity binding = deviceBindingMapper.selectByDeviceId(deviceId);
        if (binding == null) {
            throw new ForbiddenException("设备凭证错误");
        }

        try {
            String key = buildCacheKey(deviceId);
            String json = serializeToCache(binding);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("设备绑定缓存回填成功, deviceId={}, bindingId={}",
                    maskDeviceId(deviceId), binding.getId());
        } catch (Exception e) {
            log.warn("设备绑定缓存回填失败, deviceId={}", maskDeviceId(deviceId));
        }

        return binding;
    }

    /**
     * 序列化设备绑定为缓存 JSON（只保存最小字段，不保存明文 Token）。
     */
    private String serializeToCache(DeviceBindingEntity binding) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("bindingId", binding.getId());
        root.put("userId", binding.getUserId());
        root.put("credentialHash", binding.getCredentialHash());
        root.put("status", binding.getStatus());
        root.put("sourceType", binding.getSourceType());
        return root.toString();
    }

    private boolean isValidCacheEntry(JsonNode root) {
        return root.has("bindingId") && root.has("userId")
                && root.has("credentialHash") && root.has("status")
                && root.has("sourceType");
    }

    private static String buildCacheKey(String deviceId) {
        return REDIS_BEHAVIOR_DEVICE_BINDING_PREFIX + deviceId;
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 3) {
            return deviceId;
        }
        return deviceId.substring(0, 3) + "...";
    }
}
