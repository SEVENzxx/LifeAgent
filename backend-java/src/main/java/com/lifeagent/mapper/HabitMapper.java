package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.HabitEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 日常习惯 Mapper。
 */
public interface HabitMapper extends BaseMapper<HabitEntity> {

    /**
     * 使用 ON CONFLICT DO NOTHING 安全插入习惯。
     * source_message_id 唯一约束保证同一 USER 消息不会创建多个 Habit。
     */
    int insertIgnore(HabitEntity entity);

    /**
     * 乐观锁条件更新习惯状态和版本。
     *
     * @return 影响行数（0 表示版本冲突或状态不允许）
     */
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("newStatus") String newStatus,
                                @Param("oldStatus") String oldStatus,
                                @Param("version") Integer version,
                                @Param("now") Instant now);

    /**
     * 查询用户最近 ACTIVE/PAUSED 习惯。
     */
    List<HabitEntity> selectRecentByUserId(@Param("userId") Long userId,
                                           @Param("limit") int limit);

    /**
     * 按 source_message_id 查询习惯（幂等检查）。
     */
    HabitEntity selectBySourceMessage(@Param("sourceMessageId") Long sourceMessageId);

    /**
     * 根据名称查询用户下名称完全匹配的 ACTIVE/PAUSED 习惯。
     */
    List<HabitEntity> selectByName(@Param("userId") Long userId,
                                   @Param("name") String name);

    /**
     * 查询所有 ACTIVE 且未超过 end_date 的习惯（用于补齐扫描）。
     */
    List<HabitEntity> selectActiveForScheduling(@Param("now") Instant now,
                                                @Param("timezone") String defaultTimezone);
}
