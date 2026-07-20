package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.HabitExecutionEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 习惯执行 Mapper。
 */
public interface HabitExecutionMapper extends BaseMapper<HabitExecutionEntity> {

    /**
     * 使用 ON CONFLICT DO NOTHING 安全插入执行记录。
     * (habit_id, occurrence_key) 唯一约束保证同一节点不会重复创建。
     */
    int insertIgnore(HabitExecutionEntity entity);

    /**
     * 乐观锁条件更新执行状态。
     *
     * @return 影响行数
     */
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("newStatus") String newStatus,
                                @Param("oldStatus") String oldStatus,
                                @Param("version") Integer version,
                                @Param("now") Instant now);

    /**
     * 查询习惯最近一次已发送但未完成的执行。
     */
    HabitExecutionEntity selectRecentDelivered(@Param("habitId") Long habitId);

    /**
     * 查询用户最近一次已发送但未完成的执行（跨所有习惯）。
     */
    HabitExecutionEntity selectUserRecentDelivered(@Param("userId") Long userId);

    /**
     * 查询习惯下指定状态的执行。
     */
    List<HabitExecutionEntity> selectByHabitAndStatus(@Param("habitId") Long habitId,
                                                       @Param("status") String status);

    /**
     * 取消习惯下所有未来执行。
     */
    int cancelByHabit(@Param("habitId") Long habitId,
                      @Param("now") Instant now);

    /**
     * 按 completion_message_id 查询执行（幂等检查）。
     */
    HabitExecutionEntity selectByCompletionMessage(@Param("messageId") Long messageId);
}
