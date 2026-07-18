package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.MonitoredAppEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 已监测 App Mapper。
 */
public interface MonitoredAppMapper extends BaseMapper<MonitoredAppEntity> {

    /**
     * UPSERT：插入或更新 display_name 和 last_seen_at。
     */
    int upsert(@Param("userId") Long userId,
               @Param("appKey") String appKey,
               @Param("displayName") String displayName,
               @Param("eventTime") Instant eventTime,
               @Param("now") Instant now);

    /**
     * 查询用户的 App。
     */
    List<MonitoredAppEntity> selectByUserId(@Param("userId") Long userId);

    /**
     * 按用户和 app_key 查询。
     */
    MonitoredAppEntity selectByUserAndKey(@Param("userId") Long userId,
                                           @Param("appKey") String appKey);
}
