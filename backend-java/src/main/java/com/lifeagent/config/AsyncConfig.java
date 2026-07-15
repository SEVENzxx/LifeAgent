package com.lifeagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步任务执行器配置。
 *
 * <p>AI 处理使用单线程执行器，保证同一用户的 AI 任务按提交顺序处理。</p>
 */
@Configuration
public class AsyncConfig {

    /** AI 单线程执行器 Bean 名称 */
    public static final String AI_TASK_EXECUTOR = "aiTaskExecutor";

    @Bean(name = AI_TASK_EXECUTOR)
    public ExecutorService aiTaskExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
