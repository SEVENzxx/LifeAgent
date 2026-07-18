package com.lifeagent.controller;

import com.lifeagent.service.ActivityWindowService;
import com.lifeagent.service.BehaviorEventService;
import com.lifeagent.service.DeviceBindingService;
import com.lifeagent.common.ApiResponse;
import com.lifeagent.common.Constants;
import com.lifeagent.dto.behavior.ActivityWindowFacts;
import com.lifeagent.dto.behavior.ActivityWindowQuery;
import com.lifeagent.dto.behavior.BehaviorEventRequest;
import com.lifeagent.dto.behavior.BehaviorEventResponse;
import com.lifeagent.entity.DeviceBindingEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * App 行为事件控制器。
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@Tag(name = "行为事件", description = "App 行为事件上传与窗口查询")
public class BehaviorEventController {

    private final BehaviorEventService behaviorEventService;
    private final ActivityWindowService activityWindowService;
    private final DeviceBindingService deviceBindingService;

    @Operation(summary = "上传行为事件", description = "上传一条 App OPEN/CLOSE 原始事件")
    @PostMapping(Constants.BEHAVIOR_EVENT_PATH)
    public ApiResponse<BehaviorEventResponse> uploadEvent(
            @RequestHeader(Constants.HEADER_DEVICE_ID) String deviceId,
            @RequestHeader(Constants.HEADER_DEVICE_TOKEN) String token,
            @Valid @RequestBody BehaviorEventRequest request) {
        DeviceBindingEntity binding = deviceBindingService.authenticate(deviceId, token);
        BehaviorEventResponse response = behaviorEventService.processEvent(request, binding);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "查询活动窗口", description = "查询指定时间窗口内的 App 活动聚合和可选的区间明细")
    @GetMapping(Constants.BEHAVIOR_WINDOW_PATH)
    public ApiResponse<ActivityWindowFacts> queryWindow(
            @RequestHeader(Constants.HEADER_DEVICE_ID) String deviceId,
            @RequestHeader(Constants.HEADER_DEVICE_TOKEN) String token,
            @Valid ActivityWindowQuery query) {
        DeviceBindingEntity binding = deviceBindingService.authenticate(deviceId, token);
        ActivityWindowFacts facts = activityWindowService.queryWindow(
                query.getFrom(), query.getTo(), query.getAppKey(),
                query.isIncludeIntervals(), binding.getUserId(), binding.getId());
        return ApiResponse.ok(facts);
    }
}
