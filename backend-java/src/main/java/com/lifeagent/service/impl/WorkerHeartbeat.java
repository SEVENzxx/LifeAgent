package com.lifeagent.service.impl;

import com.lifeagent.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker 运行状态探针。
 *
 * <p>当前脚手架尚未接入正式任务调度，通过周期心跳确认 Worker Profile 已正确启动。</p>
 */
@Slf4j
@Component
@Profile({Constants.WORKER_PROFILE, Constants.ALL_IN_ONE_PROFILE})
public class WorkerHeartbeat {

    /**
     * 按配置间隔记录 Worker 心跳，不修改数据库状态。
     */
    @Scheduled(fixedDelayString = Constants.WORKER_HEARTBEAT_DELAY_PROPERTY)
    public void heartbeat() {
        log.debug("Worker 心跳正常");
    }
}
