package com.lifeagent.service;

import com.lifeagent.dto.behavior.BehaviorEventRequest;
import com.lifeagent.dto.behavior.BehaviorEventResponse;
import com.lifeagent.entity.DeviceBindingEntity;

/**
 * 行为事件上传服务接口。
 */
public interface BehaviorEventService {

    /**
     * 处理单次行为事件上传。
     */
    BehaviorEventResponse processEvent(BehaviorEventRequest request, DeviceBindingEntity binding);

    /**
     * 计算规范化 payload 的 SHA-256 摘要。
     */
    String computePayloadHash(BehaviorEventRequest request);

    /**
     * 脱敏 eventId：只保留后 8 位。
     */
    static String maskEventId(String eventId) {
        if (eventId == null || eventId.length() <= 8) {
            return eventId;
        }
        return "..." + eventId.substring(eventId.length() - 8);
    }
}
