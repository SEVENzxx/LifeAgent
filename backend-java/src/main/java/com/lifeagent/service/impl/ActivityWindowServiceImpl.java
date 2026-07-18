package com.lifeagent.service.impl;

import com.lifeagent.common.Constants;
import com.lifeagent.common.exception.UnprocessableEntityException;
import com.lifeagent.config.BehaviorProperties;
import com.lifeagent.dto.behavior.ActivityWindowFacts;
import com.lifeagent.dto.behavior.ActivityWindowFacts.ActivityIntervalItem;
import com.lifeagent.dto.behavior.ActivityWindowFacts.AppActivitySummary;
import com.lifeagent.dto.behavior.ActivityWindowFacts.CurrentActivity;
import com.lifeagent.dto.behavior.ActivityWindowFacts.DurationSummary;
import com.lifeagent.dto.behavior.ActivityWindowFacts.EventMetrics;
import com.lifeagent.dto.behavior.ActivityWindowFacts.QualityCounts;
import com.lifeagent.entity.ActivityIntervalEntity;
import com.lifeagent.entity.BehaviorEventEntity;
import com.lifeagent.entity.MonitoredAppEntity;
import com.lifeagent.mapper.ActivityIntervalMapper;
import com.lifeagent.mapper.BehaviorEventMapper;
import com.lifeagent.mapper.MonitoredAppMapper;
import com.lifeagent.service.ActivityWindowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动窗口查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityWindowServiceImpl implements ActivityWindowService {

    private final BehaviorProperties properties;
    private final ActivityIntervalMapper activityIntervalMapper;
    private final BehaviorEventMapper behaviorEventMapper;
    private final MonitoredAppMapper monitoredAppMapper;
    private final Clock clock;

    @Override
    public ActivityWindowFacts queryWindow(String fromStr, String toStr,
                                            String appKey, boolean includeIntervals,
                                            long userId, long deviceBindingId) {
        Instant from = parseInstant(fromStr);
        Instant to = parseInstant(toStr);
        if (from == null || to == null) {
            throw new UnprocessableEntityException("时间格式无效");
        }
        if (!from.isBefore(to)) {
            throw new UnprocessableEntityException("from 必须早于 to");
        }
        long rangeSeconds = to.getEpochSecond() - from.getEpochSecond();
        if (rangeSeconds > properties.getQueryMaxRangeSeconds()) {
            throw new UnprocessableEntityException("查询范围超过最大限制");
        }
        Instant now = Instant.now(clock);
        if (to.isAfter(now)) {
            throw new UnprocessableEntityException("截止时间不得晚于当前时间");
        }

        List<MonitoredAppEntity> allApps = monitoredAppMapper.selectByUserId(userId);
        Map<Long, MonitoredAppEntity> appMap = allApps.stream()
                .collect(Collectors.toMap(MonitoredAppEntity::getId, a -> a));

        List<ActivityIntervalEntity> intervals = activityIntervalMapper.selectByWindow(
                deviceBindingId, from, to, appKey,
                includeIntervals ? properties.getQueryMaxIntervals() + 1 : properties.getQueryMaxIntervals() + 1);

        boolean limitExceeded = intervals.size() > properties.getQueryMaxIntervals();
        if (limitExceeded) {
            throw new UnprocessableEntityException("区间数量超过返回上限，请缩小查询范围");
        }

        if (includeIntervals && intervals.size() > properties.getQueryMaxIntervals()) {
            throw new UnprocessableEntityException("区间数量超过返回上限，请缩小查询范围");
        }

        long maxIntervalSecs = properties.getMaxIntervalSeconds();
        List<ClippedInterval> clipped = new ArrayList<>();
        for (ActivityIntervalEntity interval : intervals) {
            clipped.addAll(clipInterval(interval, from, to, maxIntervalSecs));
        }

        int exactCount = 0;
        int inferredCount = 0;
        int openCount = 0;
        int truncatedCount = 0;
        int unmatchedClose = 0;
        int redundantOpen = 0;
        int redundantClose = 0;

        for (ActivityIntervalEntity interval : intervals) {
            switch (interval.getQuality()) {
                case "EXACT" -> exactCount++;
                case "INFERRED_SWITCH" -> inferredCount++;
                case "OPEN" -> openCount++;
                case "TRUNCATED" -> truncatedCount++;
            }
        }

        List<BehaviorEventEntity> windowEvents = behaviorEventMapper
                .selectByDeviceAndTimeRange(deviceBindingId, from, to);
        unmatchedClose = countUnmatchedCloses(windowEvents);
        redundantOpen = countRedundantOpens(windowEvents);
        redundantClose = countRedundantCloses(windowEvents);

        Map<String, AppActivitySummaryBuilder> appBuilders = new LinkedHashMap<>();
        for (ClippedInterval ci : clipped) {
            MonitoredAppEntity app = appMap.get(ci.monitoredAppId);
            if (app == null) continue;
            String appKeyStr = app.getAppKey();

            AppActivitySummaryBuilder builder = appBuilders
                    .computeIfAbsent(appKeyStr, k -> new AppActivitySummaryBuilder(app));
            builder.addInterval(ci);
        }

        List<AppActivitySummary> perApp = appBuilders.values().stream()
                .map(AppActivitySummaryBuilder::build)
                .collect(Collectors.toList());

        long totalExact = perApp.stream().mapToLong(AppActivitySummary::getExactSeconds).sum();
        long totalEstimated = perApp.stream().mapToLong(AppActivitySummary::getEstimatedSeconds).sum();
        long totalObserved = computeTotalObservedSeconds(clipped);

        CurrentActivity currentActivity = null;
        for (ClippedInterval ci : clipped) {
            if (ci.quality.equals("OPEN") || ci.endAt == null) {
                MonitoredAppEntity app = appMap.get(ci.monitoredAppId);
                long estimatedSecs = Math.min(
                        ci.clippedDuration,
                        maxIntervalSecs - (ci.startAt.getEpochSecond() - ci.originalStartEpoch));
                currentActivity = CurrentActivity.builder()
                        .appKey(app != null ? app.getAppKey() : "?")
                        .displayName(app != null ? app.getDisplayName() : "?")
                        .startAt(ci.startAt)
                        .activeAtWindowEnd(true)
                        .estimatedSecondsAtWindowEnd(Math.max(0, estimatedSecs))
                        .build();
            }
        }

        boolean hasExact = exactCount > 0;
        boolean hasAnyIssue = unmatchedClose > 0 || redundantOpen > 0 || redundantClose > 0
                || inferredCount > 0 || truncatedCount > 0 || openCount > 0;
        String quality;
        if (clipped.isEmpty()) {
            quality = Constants.WINDOW_QUALITY_INSUFFICIENT;
        } else if (hasExact && !hasAnyIssue) {
            quality = Constants.WINDOW_QUALITY_SUFFICIENT;
        } else {
            quality = Constants.WINDOW_QUALITY_PARTIAL;
        }

        ActivityWindowFacts facts = ActivityWindowFacts.builder()
                .fromTime(from)
                .toTime(to)
                .timezone("Asia/Shanghai")
                .generatedAt(now)
                .coverageScope(Constants.COVERAGE_MONITORED_APPS_ONLY)
                .sourceTypes(List.of(Constants.SOURCE_TYPE_SHORTCUT))
                .quality(quality)
                .monitoredApps(perApp)
                .eventMetrics(EventMetrics.builder()
                        .eventCount(windowEvents.size())
                        .exactIntervalCount(exactCount)
                        .inferredIntervalCount(inferredCount)
                        .openIntervalCount(openCount)
                        .truncatedIntervalCount(truncatedCount)
                        .unmatchedCloseCount(unmatchedClose)
                        .redundantOpenCount(redundantOpen)
                        .redundantCloseCount(redundantClose)
                        .build())
                .duration(DurationSummary.builder()
                        .exactSeconds(totalExact)
                        .estimatedSeconds(totalEstimated)
                        .totalObservedSeconds(totalObserved)
                        .build())
                .currentActivity(currentActivity)
                .intervals(includeIntervals ? buildIntervalItems(clipped, appMap) : null)
                .build();

        log.debug("窗口查询完成, from={}, to={}, quality={}, events={}, intervals={}",
                from, to, quality, windowEvents.size(), intervals.size());

        return facts;
    }

    private List<ClippedInterval> clipInterval(ActivityIntervalEntity interval,
                                                Instant from, Instant to,
                                                long maxIntervalSecs) {
        List<ClippedInterval> result = new ArrayList<>();
        Instant startAt = interval.getStartAt();
        Instant endAt = interval.getEndAt();
        Instant maxEnd = startAt.plusSeconds(maxIntervalSecs);
        Instant effectiveEnd;
        if (endAt != null && endAt.isBefore(maxEnd)) {
            effectiveEnd = endAt;
        } else {
            effectiveEnd = maxEnd;
        }
        if (effectiveEnd.isBefore(from) || startAt.isAfter(to) || startAt.equals(to)) {
            return result;
        }
        Instant clipStart = startAt.isBefore(from) ? from : startAt;
        Instant clipEnd = effectiveEnd.isAfter(to) ? to : effectiveEnd;
        if (clipStart.isBefore(clipEnd)) {
            ClippedInterval ci = new ClippedInterval();
            ci.monitoredAppId = interval.getMonitoredAppId();
            ci.quality = interval.getQuality();
            ci.endReason = interval.getEndReason();
            ci.startAt = clipStart;
            ci.endAt = clipEnd;
            ci.clippedDuration = clipEnd.getEpochSecond() - clipStart.getEpochSecond();
            ci.intervalId = interval.getId();
            ci.originalStartEpoch = startAt.getEpochSecond();
            ci.originalEndEpoch = endAt != null ? endAt.getEpochSecond() : null;
            result.add(ci);
        }
        return result;
    }

    private static class ClippedInterval {
        long monitoredAppId;
        String quality;
        String endReason;
        Instant startAt;
        Instant endAt;
        long clippedDuration;
        Long intervalId;
        long originalStartEpoch;
        Long originalEndEpoch;
    }

    private static class AppActivitySummaryBuilder {
        final String appKey;
        final String displayName;
        long exactSeconds;
        long estimatedSeconds;
        int openCount;
        Instant lastActivityAt;
        int exactCount;
        int inferredCount;
        int truncatedCount;
        int openCountQuality;

        AppActivitySummaryBuilder(MonitoredAppEntity app) {
            this.appKey = app.getAppKey();
            this.displayName = app.getDisplayName();
        }

        void addInterval(ClippedInterval ci) {
            switch (ci.quality) {
                case "EXACT" -> {
                    exactSeconds += ci.clippedDuration;
                    exactCount++;
                }
                case "INFERRED_SWITCH", "TRUNCATED", "OPEN" -> {
                    estimatedSeconds += ci.clippedDuration;
                    if ("INFERRED_SWITCH".equals(ci.quality)) inferredCount++;
                    else if ("TRUNCATED".equals(ci.quality)) truncatedCount++;
                    else openCountQuality++;
                }
            }
            openCount++;
            if (lastActivityAt == null || ci.endAt.isAfter(lastActivityAt)) {
                lastActivityAt = ci.endAt;
            }
        }

        AppActivitySummary build() {
            return AppActivitySummary.builder()
                    .appKey(appKey)
                    .displayName(displayName)
                    .exactSeconds(exactSeconds)
                    .estimatedSeconds(estimatedSeconds)
                    .totalSeconds(exactSeconds + estimatedSeconds)
                    .openCount(openCount)
                    .lastActivityAt(lastActivityAt)
                    .qualityCounts(QualityCounts.builder()
                            .exact(exactCount)
                            .inferredSwitch(inferredCount)
                            .truncated(truncatedCount)
                            .open(openCountQuality)
                            .build())
                    .build();
        }
    }

    private long computeTotalObservedSeconds(List<ClippedInterval> clipped) {
        if (clipped.isEmpty()) return 0;
        List<ClippedInterval> sorted = clipped.stream()
                .sorted(Comparator.comparing((ClippedInterval c) -> c.startAt)
                        .thenComparing(c -> c.endAt))
                .collect(Collectors.toList());
        long total = 0;
        Instant currentStart = sorted.get(0).startAt;
        Instant currentEnd = sorted.get(0).endAt;
        for (int i = 1; i < sorted.size(); i++) {
            ClippedInterval ci = sorted.get(i);
            if (ci.startAt.isBefore(currentEnd) || ci.startAt.equals(currentEnd)) {
                if (ci.endAt.isAfter(currentEnd)) {
                    currentEnd = ci.endAt;
                }
            } else {
                total += currentEnd.getEpochSecond() - currentStart.getEpochSecond();
                currentStart = ci.startAt;
                currentEnd = ci.endAt;
            }
        }
        total += currentEnd.getEpochSecond() - currentStart.getEpochSecond();
        return total;
    }

    private List<ActivityIntervalItem> buildIntervalItems(List<ClippedInterval> clipped,
                                                           Map<Long, MonitoredAppEntity> appMap) {
        Set<Long> seenIds = new HashSet<>();
        List<ActivityIntervalItem> items = new ArrayList<>();
        for (ClippedInterval ci : clipped) {
            if (ci.intervalId != null && !seenIds.add(ci.intervalId)) continue;
            MonitoredAppEntity app = appMap.get(ci.monitoredAppId);
            items.add(ActivityIntervalItem.builder()
                    .id(ci.intervalId)
                    .appKey(app != null ? app.getAppKey() : "?")
                    .displayName(app != null ? app.getDisplayName() : "?")
                    .startAt(ci.startAt)
                    .endAt(ci.endAt)
                    .quality(ci.quality)
                    .endReason(ci.endReason)
                    .build());
        }
        return items;
    }

    private Instant parseInstant(String iso) {
        try {
            OffsetDateTime odt = OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return odt.toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private int countUnmatchedCloses(List<BehaviorEventEntity> events) {
        int count = 0;
        boolean hasOpen = false;
        for (BehaviorEventEntity e : events) {
            if ("OPEN".equals(e.getEventType())) hasOpen = true;
            else if ("CLOSE".equals(e.getEventType()) && !hasOpen) count++;
        }
        return count;
    }

    private int countRedundantOpens(List<BehaviorEventEntity> events) {
        int count = 0;
        boolean sawOpen = false;
        for (BehaviorEventEntity e : events) {
            if ("OPEN".equals(e.getEventType())) {
                if (sawOpen) count++;
                sawOpen = true;
            } else if ("CLOSE".equals(e.getEventType())) {
                sawOpen = false;
            }
        }
        return count;
    }

    private int countRedundantCloses(List<BehaviorEventEntity> events) {
        int count = 0;
        boolean expectClose = false;
        for (BehaviorEventEntity e : events) {
            if ("OPEN".equals(e.getEventType())) {
                expectClose = true;
            } else if ("CLOSE".equals(e.getEventType())) {
                if (!expectClose) count++;
            }
        }
        return count;
    }
}
