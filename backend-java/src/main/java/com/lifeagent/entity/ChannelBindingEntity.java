package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部渠道用户绑定表。
 */
@Data
@TableName("channel_bindings")
public class ChannelBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 绑定的内部用户 ID
     */
    private Long userId;

    /**
     * 渠道标识，如 MOCK、WECOM
     */
    private String channel;

    /**
     * 外部渠道的用户标识
     */
    private String externalUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
