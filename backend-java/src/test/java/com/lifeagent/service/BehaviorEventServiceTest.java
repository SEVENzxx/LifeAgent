package com.lifeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lifeagent.config.BehaviorProperties;
import com.lifeagent.dto.behavior.BehaviorEventRequest;
import com.lifeagent.mapper.BehaviorEventMapper;
import com.lifeagent.mapper.DeviceBindingMapper;
import com.lifeagent.mapper.MonitoredAppMapper;
import com.lifeagent.service.impl.BehaviorEventServiceImpl;
import com.lifeagent.service.projector.ActivityIntervalProjector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorEventServiceTest {

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-07-17T12:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final ObjectMapper objectMapper = new JsonMapper();
    private final BehaviorProperties properties = new BehaviorProperties();
    private final BehaviorEventMapper behaviorEventMapper = null;
    private final MonitoredAppMapper monitoredAppMapper = null;
    private final DeviceBindingMapper deviceBindingMapper = null;
    private final ActivityIntervalProjector intervalProjector = null;

    private BehaviorEventService createService() {
        return new BehaviorEventServiceImpl(properties, behaviorEventMapper,
                monitoredAppMapper, deviceBindingMapper, intervalProjector,
                fixedClock, objectMapper);
    }

    @Test
    void shouldComputePayloadHash() {
        BehaviorEventService service = createService();
        BehaviorEventRequest request = BehaviorEventRequest.builder()
                .eventId("evt-001")
                .appKey("wechat")
                .appName("微信")
                .eventType("OPEN")
                .eventTime("2026-07-17T12:00:00+08:00")
                .clientVersion("shortcut-v1")
                .build();

        String hash = service.computePayloadHash(request);
        assertThat(hash).isNotBlank();
        assertThat(hash.length()).isEqualTo(64);
    }

    @Test
    void shouldComputeSameHashForSameFields() {
        BehaviorEventService service = createService();
        BehaviorEventRequest r1 = BehaviorEventRequest.builder()
                .eventId("evt-001").appKey("wechat").appName("微信")
                .eventType("OPEN").eventTime("2026-07-17T12:00:00+08:00")
                .clientVersion("shortcut-v1").build();
        BehaviorEventRequest r2 = BehaviorEventRequest.builder()
                .eventId("evt-002").appKey("wechat").appName("微信")
                .eventType("OPEN").eventTime("2026-07-17T12:00:00+08:00")
                .clientVersion("shortcut-v1").build();

        assertThat(service.computePayloadHash(r1))
                .isEqualTo(service.computePayloadHash(r2));
    }

    @Test
    void shouldComputeDifferentHashForDifferentFields() {
        BehaviorEventService service = createService();
        BehaviorEventRequest r1 = BehaviorEventRequest.builder()
                .eventId("evt-001").appKey("wechat").appName("微信")
                .eventType("OPEN").eventTime("2026-07-17T12:00:00+08:00")
                .clientVersion("shortcut-v1").build();
        BehaviorEventRequest r2 = BehaviorEventRequest.builder()
                .eventId("evt-001").appKey("wechat").appName("微信")
                .eventType("CLOSE").eventTime("2026-07-17T12:00:00+08:00")
                .clientVersion("shortcut-v1").build();

        assertThat(service.computePayloadHash(r1))
                .isNotEqualTo(service.computePayloadHash(r2));
    }

    @Test
    void shouldMaskEventId() {
        assertThat(BehaviorEventService.maskEventId("0edcb8e8-f57a-4cec-b47a-9d6644e3da72"))
                .isEqualTo("...44e3da72");
        assertThat(BehaviorEventService.maskEventId("short")).isEqualTo("short");
        assertThat(BehaviorEventService.maskEventId(null)).isNull();
    }

    @Test
    void shouldComputeSameHashRegardlessOfFieldOrder() {
        BehaviorEventService service = createService();
        BehaviorEventRequest request = BehaviorEventRequest.builder()
                .eventId("evt-001")
                .appKey("telegram")
                .appName("Telegram")
                .eventType("OPEN")
                .eventTime("2026-07-17T13:00:00+08:00")
                .clientVersion("shortcut-v1")
                .build();

        String hash1 = service.computePayloadHash(request);
        String hash2 = service.computePayloadHash(request);
        assertThat(hash1).isEqualTo(hash2);
    }
}
