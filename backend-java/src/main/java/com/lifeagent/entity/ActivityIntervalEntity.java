package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 从原始事件确定性重建的活动区间。
 */
@Data
@TableName("activity_intervals")
public class ActivityIntervalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** FK → device_bindings.id */
    private Long deviceBindingId;

    /** FK → monitored_apps.id */
    private Long monitoredAppId;

    /** FK → behavior_events.id，唯一 */
    private Long startEventId;

    /** FK → behavior_events.id，可空 */
    private Long endEventId;

    /** 区间开始时间 */
    private Instant startAt;

    /** 区间结束时间，可空 */
    private Instant endAt;

    /** EXACT / INFERRED_SWITCH / TRUNCATED / OPEN */
    private String quality;

    /** EXPLICIT_CLOSE / APP_SWITCH / MAX_DURATION / STILL_OPEN */
    private String endReason;

    private Instant createdAt;

    private Instant updatedAt;
}
