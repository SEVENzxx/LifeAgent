package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.ScheduledJobEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 调度任务 Mapper。
 */
public interface ScheduledJobMapper extends BaseMapper<ScheduledJobEntity> {

    /**
     * 使用 ON CONFLICT DO NOTHING 安全插入 Job（REMINDER_DELIVERY）。
     * business_key 唯一约束保证同一 Job 不会重复创建。
     */
    int insertIgnore(ScheduledJobEntity entity);

    /**
     * 使用 ON CONFLICT DO NOTHING 安全插入 HABIT_DELIVERY Job。
     * habit_execution_id 唯一约束保证同一 Execution 不会重复创建 Job。
     */
    int insertIgnoreHabit(ScheduledJobEntity entity);

    /**
     * 扫描可领取的 Job：READY/RETRY_WAIT 且 next_run_at <= now，或租约已过期的 RUNNING。
     * 使用 FOR UPDATE SKIP LOCKED 避免竞争。
     *
     * @param limit 最大领取数量
     */
    List<ScheduledJobEntity> scanDueJobs(@Param("now") Instant now,
                                         @Param("limit") int limit);

    /**
     * 领取 Job：设置 RUNNING + lease_owner + lease_until。
     * 条件更新：只有当前是 READY/RETRY_WAIT/RUNNING（已过期）才能领取。
     *
     * @return 影响行数
     */
    int acquireLease(@Param("id") Long id,
                     @Param("owner") String owner,
                     @Param("leaseUntil") Instant leaseUntil,
                     @Param("now") Instant now);

    /**
     * Job 执行成功：更新为 SUCCEEDED。
     */
    int markSucceeded(@Param("id") Long id,
                      @Param("assistantMessageId") Long assistantMessageId,
                      @Param("now") Instant now);

    /**
     * Job 发送失败：更新为 RETRY_WAIT 并设置下次运行时间。
     */
    int markRetryWait(@Param("id") Long id,
                      @Param("nextRunAt") Instant nextRunAt,
                      @Param("errorCode") String errorCode,
                      @Param("now") Instant now);

    /**
     * Job 重试耗尽：更新为 FAILED。
     */
    int markFailed(@Param("id") Long id,
                   @Param("errorCode") String errorCode,
                   @Param("now") Instant now);

    /**
     * 取消指定 Reminder 的所有未来 Job。
     */
    int cancelByReminder(@Param("reminderId") Long reminderId,
                         @Param("now") Instant now);

    /**
     * 取消指定 Habit 的所有未来 Job。
     */
    int cancelByHabit(@Param("habitId") Long habitId,
                      @Param("now") Instant now);

    /**
     * 标记超过宽限的 Job 为 MISSED。
     */
    int markMissed(@Param("deadline") Instant deadline,
                   @Param("now") Instant now);

    /**
     * 查询指定 Reminder 下指定状态和类型的 Job。
     */
    List<ScheduledJobEntity> selectByReminderAndStatus(@Param("reminderId") Long reminderId,
                                                       @Param("status") String status);

    /**
     * 查询指定 HabitExecution 下的 Job。
     */
    List<ScheduledJobEntity> selectByHabitExecution(@Param("habitExecutionId") Long habitExecutionId);
}
