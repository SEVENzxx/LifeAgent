package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户级已监测 App 登记。
 */
@Data
@TableName("monitored_apps")
public class MonitoredAppEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 FK → users.id */
    private Long userId;

    /** 稳定 App 标识 */
    private String appKey;

    /** 最近显示名 */
    private String displayName;

    /** 首次事件时间 */
    private Instant firstSeenAt;

    /** 最近事件时间 */
    private Instant lastSeenAt;

    private Instant createdAt;

    private Instant updatedAt;
}
