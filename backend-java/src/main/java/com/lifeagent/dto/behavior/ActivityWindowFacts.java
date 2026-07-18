package com.lifeagent.dto.behavior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Activity Window 查询结果事实包。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityWindowFacts {

    private Instant fromTime;
    private Instant toTime;
    private String timezone;
    private Instant generatedAt;

    /** 覆盖声明：MONITORED_APPS_ONLY */
    private String coverageScope;

    /** 数据来源列表 */
    private List<String> sourceTypes;

    /** 窗口质量：SUFFICIENT / PARTIAL / INSUFFICIENT */
    private String quality;

    /** 已监测 App 列表 */
    private List<AppActivitySummary> monitoredApps;

    /** 事件指标 */
    private EventMetrics eventMetrics;

    /** 总时长 */
    private DurationSummary duration;

    /** 当前活动（窗口截止时仍开放的活动） */
    private CurrentActivity currentActivity;

    /** 区间明细（仅在 includeIntervals=true 时返回） */
    private List<ActivityIntervalItem> intervals;

    /** 每 App 汇总 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppActivitySummary {
        private String appKey;
        private String displayName;
        private long exactSeconds;
        private long estimatedSeconds;
        private long totalSeconds;
        private int openCount;
        private Instant lastActivityAt;
        private QualityCounts qualityCounts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityCounts {
        private int exact;
        private int inferredSwitch;
        private int truncated;
        private int open;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventMetrics {
        private int eventCount;
        private int exactIntervalCount;
        private int inferredIntervalCount;
        private int openIntervalCount;
        private int truncatedIntervalCount;
        private int unmatchedCloseCount;
        private int redundantOpenCount;
        private int redundantCloseCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DurationSummary {
        private long exactSeconds;
        private long estimatedSeconds;
        private long totalObservedSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentActivity {
        private String appKey;
        private String displayName;
        private Instant startAt;
        private boolean activeAtWindowEnd;
        private long estimatedSecondsAtWindowEnd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityIntervalItem {
        private Long id;
        private String appKey;
        private String displayName;
        private Instant startAt;
        private Instant endAt;
        private String quality;
        private String endReason;
    }
}
