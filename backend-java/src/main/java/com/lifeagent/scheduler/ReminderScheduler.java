package com.lifeagent.scheduler;

import com.lifeagent.service.ScheduledJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提醒调度器：每 5 秒扫描到期 Job。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ScheduledJobService scheduledJobService;

    /**
     * 每 5 秒扫描并执行一次到期 Job。
     */
    @Scheduled(fixedDelayString = "${lifeagent.ai.reminder-scan-interval-ms:5000}")
    public void scanReminderJobs() {
        log.debug("提醒扫描开始");
        try {
            scheduledJobService.scanAndExecute();
        } catch (Exception e) {
            log.error("提醒扫描执行异常", e);
        }
    }
}
