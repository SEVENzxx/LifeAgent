package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.BehaviorEventEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 行为事件 Mapper。
 */
public interface BehaviorEventMapper extends BaseMapper<BehaviorEventEntity> {

    /**
     * 使用 ON CONFLICT DO NOTHING 幂等插入。
     */
    int insertIgnore(BehaviorEventEntity entity);

    /**
     * 按 eventId 查询事件（含 payload_hash 用于冲突检测）。
     */
    BehaviorEventEntity selectByDeviceAndEventId(@Param("deviceBindingId") Long deviceBindingId,
                                                  @Param("eventId") String eventId);

    /**
     * 查询依赖范围内的事件（按稳定顺序）。
     */
    List<BehaviorEventEntity> selectByDeviceAndTimeRange(@Param("deviceBindingId") Long deviceBindingId,
                                                          @Param("fromTime") Instant fromTime,
                                                          @Param("toTime") Instant toTime);
}
