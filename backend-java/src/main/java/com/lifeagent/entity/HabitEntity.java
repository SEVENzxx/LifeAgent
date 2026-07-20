package com.lifeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 日常习惯正式表，只保存已确认规则。
 */
@Data
@TableName("habits")
public class HabitEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 习惯名称，最长 200 字符 */
    private String name;

    /** JSONB 字符串数组，1～10 个已排序去重 HH:mm */
    private String dailyTimes;

    /** 用户 IANA 时区 */
    private String timezone;

    /** 用户本地开始日期 */
    private LocalDate startDate;

    /** 可空，不得早于开始日期 */
    private LocalDate endDate;

    /** 状态：ACTIVE/PAUSED/CANCELLED/ENDED */
    private String status;

    /** 确认创建的 USER 消息 ID */
    private Long sourceMessageId;

    /** 乐观锁版本 */
    @Version
    private Integer version;

    private Instant createdAt;

    private Instant updatedAt;
}
