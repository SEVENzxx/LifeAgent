package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.ReminderEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 提醒 Mapper。
 */
public interface ReminderMapper extends BaseMapper<ReminderEntity> {

    /**
     * 使用 ON CONFLICT DO NOTHING 安全插入提醒。
     * source_message_id 唯一约束保证同一 USER 消息不会创建多个 Reminder。
     */
    int insertIgnore(ReminderEntity entity);

    /**
     * 乐观锁条件更新提醒状态。
     *
     * @return 影响行数（0 表示版本冲突或状态不允许）
     */
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("newStatus") String newStatus,
                                @Param("oldStatus") String oldStatus,
                                @Param("version") Integer version,
                                @Param("now") Instant now);

    /**
     * 查询用户最近一条有效 Reminder（ACTIVE/ACKNOWLEDGED/DELIVERED）。
     */
    ReminderEntity selectRecentByUserId(@Param("userId") Long userId);

    /**
     * 查询指定用户和 source_message_id 的提醒（幂等检查）。
     */
    ReminderEntity selectBySourceMessage(@Param("sourceMessageId") Long sourceMessageId);

    /**
     * 查询用户未完成的定时提醒（ACTIVE/DELIVERED/ACKNOWLEDGED）。
     */
    List<ReminderEntity> selectActiveByUserId(@Param("userId") Long userId);
}
