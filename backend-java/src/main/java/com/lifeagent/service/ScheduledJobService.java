package com.lifeagent.service;

/**
 * 调度任务服务，负责到期扫描、租约领取和执行。
 */
public interface ScheduledJobService {

    /**
     * 扫描并执行一次到期 Job。
     */
    void scanAndExecute();
}
