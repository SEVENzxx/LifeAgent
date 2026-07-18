package com.lifeagent.service.projector;

import com.lifeagent.common.Constants;
import com.lifeagent.entity.ActivityIntervalEntity;
import com.lifeagent.entity.BehaviorEventEntity;
import com.lifeagent.mapper.ActivityIntervalMapper;
import com.lifeagent.mapper.BehaviorEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 确定性局部区间重建器。
 *
 * <p>每次新事件到达时，在同一数据库事务中锁定设备串行处理：
 * <ol>
 *   <li>截断超过最大时长的旧开放区间；</li>
 *   <li>计算受影响和依赖范围；</li>
 *   <li>读取依赖范围内的全部事件；</li>
 *   <li>从依赖范围起点的无活动状态重放；</li>
 *   <li>删除受影响的旧区间并写入新结果。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityIntervalProjector {

    private final BehaviorEventMapper behaviorEventMapper;
    private final ActivityIntervalMapper activityIntervalMapper;

    private static class ProjectionState {
        final long deviceBindingId;
        final long maxIntervalSeconds;
        Instant now;

        BehaviorEventEntity currentOpenEvent;
        Long currentMonitoredAppId;

        BehaviorEventEntity lastClosedEvent;
        Long lastClosedMonitoredAppId;
        Instant lastClosedAt;

        int redundantOpenCount;
        int redundantCloseCount;
        int unmatchedCloseCount;

        final List<ActivityIntervalEntity> rebuilt = new ArrayList<>();

        Instant minEventTime;
        Instant maxEventTime;

        ProjectionState(long deviceBindingId, long maxIntervalSeconds) {
            this.deviceBindingId = deviceBindingId;
            this.maxIntervalSeconds = maxIntervalSeconds;
        }

        boolean hasCurrentActivity() {
            return currentOpenEvent != null;
        }
    }

    /**
     * 重建指定事件影响范围内的区间。
     */
    public void rebuildForEvent(BehaviorEventEntity event, long deviceBindingId,
                                 long maxIntervalSeconds, Instant now) {
        ProjectionState state = new ProjectionState(deviceBindingId, maxIntervalSeconds);
        state.now = now;

        Instant maxAllowedEnd = event.getEventTime().minusSeconds(maxIntervalSeconds);
        int truncated = activityIntervalMapper.truncateOpenIntervals(deviceBindingId,
                maxAllowedEnd, now);
        if (truncated > 0) {
            log.debug("截断 {} 个过期开放区间, deviceBindingId={}", truncated, deviceBindingId);
        }

        Instant affectedFrom = event.getEventTime().minusSeconds(maxIntervalSeconds);
        Instant affectedTo = event.getEventTime().plusSeconds(maxIntervalSeconds);
        Instant dependencyFrom = affectedFrom.minusSeconds(maxIntervalSeconds);
        Instant dependencyTo = affectedTo.plusSeconds(maxIntervalSeconds);

        List<BehaviorEventEntity> events = behaviorEventMapper
                .selectByDeviceAndTimeRange(deviceBindingId, dependencyFrom, dependencyTo);

        if (events.isEmpty()) {
            return;
        }

        state.minEventTime = events.get(0).getEventTime();
        state.maxEventTime = events.get(events.size() - 1).getEventTime();

        replay(events, state);

        int deleted = activityIntervalMapper.deleteOverlapping(deviceBindingId, affectedFrom, affectedTo);

        List<ActivityIntervalEntity> toInsert = new ArrayList<>();
        for (ActivityIntervalEntity interval : state.rebuilt) {
            if (interval.getStartAt().isBefore(affectedTo)
                    && (interval.getEndAt() == null || interval.getEndAt().isAfter(affectedFrom))) {
                toInsert.add(interval);
            }
        }
        if (!toInsert.isEmpty()) {
            activityIntervalMapper.batchInsert(toInsert);
        }

        log.debug("区间重建完成, deviceBindingId={}, deleted={}, inserted={}",
                deviceBindingId, deleted, toInsert.size());
    }

    private void replay(List<BehaviorEventEntity> events, ProjectionState state) {
        for (BehaviorEventEntity event : events) {
            String type = event.getEventType();
            long eventAppId = event.getMonitoredAppId();

            if (Constants.EVENT_TYPE_OPEN.equals(type)) {
                handleOpen(event, eventAppId, state);
            } else if (Constants.EVENT_TYPE_CLOSE.equals(type)) {
                handleClose(event, eventAppId, state);
            }
        }
    }

    private void handleOpen(BehaviorEventEntity event, long appId, ProjectionState state) {
        if (!state.hasCurrentActivity()) {
            startNewInterval(event, appId, state);
            return;
        }
        if (state.currentMonitoredAppId != null && state.currentMonitoredAppId.equals(appId)) {
            state.redundantOpenCount++;
            log.debug("重复 OPEN 忽略, eventId={}", event.getEventId());
            return;
        }
        closeCurrentInterval(event.getEventTime(), state.currentMonitoredAppId,
                Constants.INTERVAL_QUALITY_INFERRED_SWITCH,
                Constants.END_REASON_APP_SWITCH, event, state);
        state.lastClosedEvent = null;
        state.lastClosedMonitoredAppId = null;
        startNewInterval(event, appId, state);
    }

    private void handleClose(BehaviorEventEntity event, long appId, ProjectionState state) {
        if (!state.hasCurrentActivity()) {
            if (canExtendLastClosed(event, appId, state)) {
                extendLastClosed(event, state);
                return;
            }
            state.unmatchedCloseCount++;
            log.debug("孤立 CLOSE, eventId={}", event.getEventId());
            return;
        }
        if (state.currentMonitoredAppId != null && state.currentMonitoredAppId.equals(appId)) {
            closeCurrentInterval(event.getEventTime(), appId,
                    Constants.INTERVAL_QUALITY_EXACT,
                    Constants.END_REASON_EXPLICIT_CLOSE, event, state);
            return;
        }
        state.unmatchedCloseCount++;
        log.debug("非活动 App CLOSE 忽略, eventId={}", event.getEventId());
    }

    private boolean canExtendLastClosed(BehaviorEventEntity event, long appId, ProjectionState state) {
        return state.lastClosedEvent != null
                && state.lastClosedMonitoredAppId != null
                && state.lastClosedMonitoredAppId.equals(appId);
    }

    private void extendLastClosed(BehaviorEventEntity event, ProjectionState state) {
        ActivityIntervalEntity lastInterval = state.rebuilt.isEmpty() ? null
                : state.rebuilt.get(state.rebuilt.size() - 1);
        if (lastInterval != null && lastInterval.getId() == null
                && lastInterval.getEndEventId() != null
                && lastInterval.getEndEventId().equals(state.lastClosedEvent.getId())) {
            lastInterval.setEndAt(event.getEventTime());
            lastInterval.setEndEventId(event.getId());
            state.redundantCloseCount++;
            log.debug("延长 CLOSE 片段, eventId={}", event.getEventId());
        }
        state.lastClosedEvent = event;
    }

    private void startNewInterval(BehaviorEventEntity event, long appId, ProjectionState state) {
        ActivityIntervalEntity interval = new ActivityIntervalEntity();
        interval.setDeviceBindingId(state.deviceBindingId);
        interval.setMonitoredAppId(appId);
        interval.setStartEventId(event.getId());
        interval.setStartAt(event.getEventTime());
        interval.setEndAt(null);
        interval.setQuality(Constants.INTERVAL_QUALITY_OPEN);
        interval.setEndReason(Constants.END_REASON_STILL_OPEN);
        interval.setCreatedAt(state.now);
        interval.setUpdatedAt(state.now);
        state.rebuilt.add(interval);

        state.currentOpenEvent = event;
        state.currentMonitoredAppId = appId;
        state.lastClosedEvent = null;
        state.lastClosedMonitoredAppId = null;
    }

    private void closeCurrentInterval(Instant closeTime, long appId,
                                       String quality, String endReason,
                                       BehaviorEventEntity event, ProjectionState state) {
        for (int i = state.rebuilt.size() - 1; i >= 0; i--) {
            ActivityIntervalEntity interval = state.rebuilt.get(i);
            if (interval.getEndAt() == null
                    && interval.getMonitoredAppId().equals(appId)) {
                interval.setEndAt(closeTime);
                interval.setEndEventId(event.getId());
                interval.setQuality(quality);
                interval.setEndReason(endReason);
                interval.setUpdatedAt(state.now);
                break;
            }
        }
        state.currentOpenEvent = null;
        state.currentMonitoredAppId = null;
        state.lastClosedEvent = event;
        state.lastClosedMonitoredAppId = appId;
        state.lastClosedAt = closeTime;
    }
}
