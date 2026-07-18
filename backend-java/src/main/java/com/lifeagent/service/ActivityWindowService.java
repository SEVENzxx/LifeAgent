package com.lifeagent.service;

import com.lifeagent.dto.behavior.ActivityWindowFacts;

/**
 * 活动窗口查询服务接口。
 *
 * <p>支持任意半开时间窗裁剪，跨天区间按窗口边界物理分隔统计。</p>
 */
public interface ActivityWindowService {

    /**
     * 查询活动窗口事实包。
     */
    ActivityWindowFacts queryWindow(String fromStr, String toStr,
                                    String appKey, boolean includeIntervals,
                                    long userId, long deviceBindingId);
}
