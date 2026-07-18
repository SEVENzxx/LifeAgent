package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 不可变原始行为事件。
 */
@Data
@TableName("behavior_events")
public class BehaviorEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** FK → device_bindings.id */
    private Long deviceBindingId;

    /** FK → monitored_apps.id */
    private Long monitoredAppId;

    /** 客户端生成 UUID */
    private String eventId;

    /** OPEN / CLOSE */
    private String eventType;

    /** 设备观察时间 */
    private Instant eventTime;

    /** 服务端接收时间 */
    private Instant receivedAt;

    /** 客户端版本 */
    private String clientVersion;

    /** 规范化正文 SHA-256 */
    private String payloadHash;

    private Instant createdAt;
}
