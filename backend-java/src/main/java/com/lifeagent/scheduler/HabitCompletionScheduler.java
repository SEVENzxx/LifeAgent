package com.lifeagent.scheduler;

import com.lifeagent.service.HabitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 习惯补齐调度器：每 60 秒为 ACTIVE 习惯补齐下一执行。
 *
 * <p>停服重启时只创建当前时间之后的下一节点，不补发停机期间错过的提醒。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HabitCompletionScheduler {

    private final HabitService habitService;
    private final Clock clock;

    /**
     * 每 60 秒扫描一次 ACTIVE 习惯，补齐下一执行。
     */
    @Scheduled(fixedDelayString = "${lifeagent.ai.habit-schedule-scan-interval-ms:60000}")
    public void scheduleNextExecutions() {
        log.debug("习惯补齐扫描开始");
        try {
            Instant now = Instant.now(clock);
            int created = habitService.scheduleNextExecutions(now);
            if (created > 0) {
                log.info("习惯补齐完成, created={}", created);
            }
        } catch (Exception e) {
            log.error("习惯补齐扫描异常", e);
        }
    }
}
