package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 设备凭证和可撤销状态。
 */
@Data
@TableName("device_bindings")
public class DeviceBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 FK → users.id */
    private Long userId;

    /** 稳定外部设备键 */
    private String deviceId;

    /** 设备显示名 */
    private String displayName;

    /** 数据来源：SHORTCUT */
    private String sourceType;

    /** Token SHA-256 摘要 */
    private String credentialHash;

    /** ACTIVE / REVOKED */
    private String status;

    private Instant createdAt;

    private Instant updatedAt;
}
