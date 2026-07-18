package com.lifeagent.config;

import com.lifeagent.common.Constants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * App 行为事件跟踪相关配置属性。
 *
 * <p>设备绑定已改为 PostgreSQL 事实源 + Redis 缓存，不再使用环境变量配置设备凭证。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lifeagent.behavior")
public class BehaviorProperties {

    /** eventTime 最多比服务器时间晚多少秒 */
    private long maxFutureSkewSeconds = Constants.BEHAVIOR_MAX_FUTURE_SKEW_SECONDS;

    /** 接受晚到事件的最大历史秒数 */
    private long maxPastAgeSeconds = Constants.BEHAVIOR_MAX_PAST_AGE_SECONDS;

    /** 单次活动最大区间秒数 */
    private long maxIntervalSeconds = Constants.BEHAVIOR_MAX_INTERVAL_SECONDS;

    /** 查询最大范围秒数 */
    private long queryMaxRangeSeconds = Constants.BEHAVIOR_QUERY_MAX_RANGE_SECONDS;

    /** 查询返回最大区间数 */
    private int queryMaxIntervals = Constants.BEHAVIOR_QUERY_MAX_INTERVALS;
}
