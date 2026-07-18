package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.ActivityIntervalEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 活动区间 Mapper。
 */
public interface ActivityIntervalMapper extends BaseMapper<ActivityIntervalEntity> {

    /**
     * 查询与指定设备和时间范围重叠的区间（含跨越边界的完整区间）。
     */
    List<ActivityIntervalEntity> selectOverlapping(@Param("deviceBindingId") Long deviceBindingId,
                                                    @Param("fromTime") Instant fromTime,
                                                    @Param("toTime") Instant toTime);

    /**
     * 删除与指定设备和时间范围重叠的区间。
     */
    int deleteOverlapping(@Param("deviceBindingId") Long deviceBindingId,
                          @Param("fromTime") Instant fromTime,
                          @Param("toTime") Instant toTime);

    /**
     * 查询设备当前开放区间。
     */
    ActivityIntervalEntity selectOpenByDevice(@Param("deviceBindingId") Long deviceBindingId);

    /**
     * 截断超过最大时长的开放区间。
     */
    int truncateOpenIntervals(@Param("deviceBindingId") Long deviceBindingId,
                              @Param("maxInstant") Instant maxInstant,
                              @Param("now") Instant now);

    /**
     * 批量插入区间。
     */
    int batchInsert(@Param("list") List<ActivityIntervalEntity> intervals);

    /**
     * 查询窗口范围内的区间（含跨越边界的完整区间）。
     */
    List<ActivityIntervalEntity> selectByWindow(@Param("deviceBindingId") Long deviceBindingId,
                                                 @Param("fromTime") Instant fromTime,
                                                 @Param("toTime") Instant toTime,
                                                 @Param("appKey") String appKey,
                                                 @Param("limit") int limit);
}
