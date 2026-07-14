package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表。Mock 外部用户对应的内部用户。
 */
@Data
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户状态：ACTIVE
     */
    private String status;

    /**
     * IANA 时区，P0 固定 Asia/Shanghai
     */
    private String timezone;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
