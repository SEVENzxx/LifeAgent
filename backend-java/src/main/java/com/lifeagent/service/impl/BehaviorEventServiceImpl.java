package com.lifeagent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifeagent.common.Constants;
import com.lifeagent.common.exception.ConflictException;
import com.lifeagent.common.exception.ForbiddenException;
import com.lifeagent.common.exception.UnprocessableEntityException;
import com.lifeagent.config.BehaviorProperties;
import com.lifeagent.dto.behavior.BehaviorEventRequest;
import com.lifeagent.dto.behavior.BehaviorEventResponse;
import com.lifeagent.entity.BehaviorEventEntity;
import com.lifeagent.entity.DeviceBindingEntity;
import com.lifeagent.entity.MonitoredAppEntity;
import com.lifeagent.mapper.BehaviorEventMapper;
import com.lifeagent.mapper.DeviceBindingMapper;
import com.lifeagent.mapper.MonitoredAppMapper;
import com.lifeagent.service.BehaviorEventService;
import com.lifeagent.service.projector.ActivityIntervalProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 行为事件上传服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorEventServiceImpl implements BehaviorEventService {

    private final BehaviorProperties properties;
    private final BehaviorEventMapper behaviorEventMapper;
    private final MonitoredAppMapper monitoredAppMapper;
    private final DeviceBindingMapper deviceBindingMapper;
    private final ActivityIntervalProjector intervalProjector;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * 处理单次行为事件上传。
     */
    @Override
    @Transactional
    public BehaviorEventResponse processEvent(BehaviorEventRequest request,
                                               DeviceBindingEntity binding) {
        Instant now = Instant.now(clock);

        // 1. 使用 SELECT FOR UPDATE 锁定设备行，串行处理同设备事件
        DeviceBindingEntity locked = deviceBindingMapper.selectByDeviceIdForUpdate(binding.getDeviceId());
        if (locked == null || !Constants.DEVICE_STATUS_ACTIVE.equals(locked.getStatus())) {
            throw new ForbiddenException("设备状态异常");
        }

        // 2. 解析 eventTime
        Instant eventTime = parseEventTime(request.getEventTime());
        if (eventTime == null) {
            throw new UnprocessableEntityException("事件时间格式无效");
        }

        // 3. 时间校验
        Instant maxFuture = now.plusSeconds(properties.getMaxFutureSkewSeconds());
        if (eventTime.isAfter(maxFuture)) {
            throw new UnprocessableEntityException("事件时间超过未来容差");
        }
        Instant maxPast = now.minusSeconds(properties.getMaxPastAgeSeconds());
        if (eventTime.isBefore(maxPast)) {
            throw new UnprocessableEntityException("事件时间超过历史接收上限");
        }

        // 4. 幂等检查
        BehaviorEventEntity existing = behaviorEventMapper.selectByDeviceAndEventId(
                binding.getId(), request.getEventId());
        if (existing != null) {
            String computedHash = computePayloadHash(request);
            if (!computedHash.equals(existing.getPayloadHash())) {
                log.warn("相同 eventId 但正文冲突, eventId={}",
                        BehaviorEventService.maskEventId(request.getEventId()));
                throw new ConflictException("事件 ID 已存在但正文不同");
            }
            return BehaviorEventResponse.builder()
                    .eventId(request.getEventId())
                    .result(Constants.EVENT_RESULT_DUPLICATE)
                    .build();
        }

        // 5. UPSERT monitored_app
        monitoredAppMapper.upsert(
                binding.getUserId(),
                request.getAppKey(),
                request.getAppName(),
                eventTime,
                now);
        MonitoredAppEntity app = monitoredAppMapper.selectByUserAndKey(
                binding.getUserId(), request.getAppKey());
        if (app == null) {
            throw new RuntimeException("App 登记失败");
        }

        // 6. 插入事件
        BehaviorEventEntity event = new BehaviorEventEntity();
        event.setDeviceBindingId(binding.getId());
        event.setMonitoredAppId(app.getId());
        event.setEventId(request.getEventId());
        event.setEventType(request.getEventType());
        event.setEventTime(eventTime);
        event.setReceivedAt(now);
        event.setClientVersion(request.getClientVersion());
        event.setPayloadHash(computePayloadHash(request));
        event.setCreatedAt(now);

        int rows = behaviorEventMapper.insertIgnore(event);
        if (rows == 0) {
            BehaviorEventEntity rechecked = behaviorEventMapper.selectByDeviceAndEventId(
                    binding.getId(), request.getEventId());
            if (rechecked != null) {
                return BehaviorEventResponse.builder()
                        .eventId(request.getEventId())
                        .result(Constants.EVENT_RESULT_DUPLICATE)
                        .build();
            }
            throw new RuntimeException("事件插入失败");
        }

        // 7. 局部区间重建
        intervalProjector.rebuildForEvent(event, binding.getId(),
                properties.getMaxIntervalSeconds(), now);

        log.info("行为事件处理成功, eventId={}, type={}, appKey={}, deviceBindingId={}",
                BehaviorEventService.maskEventId(request.getEventId()), request.getEventType(),
                request.getAppKey(), binding.getId());

        return BehaviorEventResponse.builder()
                .eventId(request.getEventId())
                .result(Constants.EVENT_RESULT_ACCEPTED)
                .build();
    }

    /**
     * 解析带偏移 ISO 8601 时间字符串。
     */
    private Instant parseEventTime(String eventTime) {
        try {
            OffsetDateTime odt = OffsetDateTime.parse(eventTime,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return odt.toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 计算规范化 payload 的 SHA-256 摘要。
     */
    @Override
    public String computePayloadHash(BehaviorEventRequest request) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("appKey", request.getAppKey());
            node.put("appName", request.getAppName());
            node.put("eventType", request.getEventType());
            node.put("eventTime", request.getEventTime());
            node.put("clientVersion", request.getClientVersion());

            String canonical = objectMapper.writeValueAsString(node);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("payload 哈希计算失败", e);
        }
    }
}
