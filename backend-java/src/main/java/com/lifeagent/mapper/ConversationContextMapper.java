package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.ConversationContextEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 对话上下文 Mapper。
 */
public interface ConversationContextMapper extends BaseMapper<ConversationContextEntity> {

    /**
     * 更新摘要和截止消息 ID。
     * summary 与 summarized_through_message_id 必须同时非空或同时为空。
     */
    int updateSummary(@Param("userId") Long userId,
                      @Param("summary") String summary,
                      @Param("summarizedThroughMessageId") Long summarizedThroughMessageId,
                      @Param("now") LocalDateTime now);

    /**
     * 更新最近语义识别结果。
     * lastIntent、lastConfidence、lastResolvedMessageId 必须同时非空或同时为空。
     */
    int updateLastResolution(@Param("userId") Long userId,
                             @Param("lastIntent") String lastIntent,
                             @Param("lastConfidence") String lastConfidence,
                             @Param("lastResolvedMessageId") Long lastResolvedMessageId,
                             @Param("now") LocalDateTime now);

    /**
     * 插入或忽略（首次创建时使用）。
     */
    int insertIgnore(ConversationContextEntity entity);
}
