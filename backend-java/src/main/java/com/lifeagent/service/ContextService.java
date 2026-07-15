package com.lifeagent.service;

import com.lifeagent.dto.turn.ContextPackage;
import com.lifeagent.dto.turn.TurnResolutionResponse;

/**
 * LLM 上下文服务，负责 Redis Cache-Aside 和 PostgreSQL 回源。
 */
public interface ContextService {

    /**
     * 获取或构建当前用户的 AI 上下文。
     *
     * @param userId         内部用户 ID
     * @param currentMessage 当前用户消息正文
     * @return 裁剪后的上下文包
     */
    ContextPackage getOrBuildContext(Long userId, String currentMessage);

    /**
     * AI 解析完成后更新上下文状态（摘要、Intent、置信度）。
     * 不更新的情况：AI_UNAVAILABLE、未触发摘要、摘要失败。
     *
     * @param userId   内部用户 ID
     * @param response AI 解析响应
     */
    void updateAfterResolution(Long userId, ContextPackage context, TurnResolutionResponse response);

    /**
     * 失效指定用户的 Redis 上下文缓存。
     */
    void evictCache(Long userId);
}
