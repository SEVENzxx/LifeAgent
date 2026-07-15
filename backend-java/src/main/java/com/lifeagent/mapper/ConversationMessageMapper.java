package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    /**
     * 使用 PostgreSQL ON CONFLICT DO NOTHING 安全插入 USER 消息。
     * 返回 1 表示插入成功，0 表示唯一键冲突（重复消息）。
     * SQL 定义在 ConversationMessageMapper.xml。
     */
    int insertIgnore(ConversationMessageEntity entity);

    /**
     * 使用 ON CONFLICT DO NOTHING 安全插入 ASSISTANT 消息。
     * ASSISTANT 唯一约束为 idempotency_key WHERE role = 'ASSISTANT'。
     */
    int insertIgnoreAssistant(ConversationMessageEntity entity);

    /**
     * 更新 ASSISTANT 投递状态和发送时间。
     *
     * @param id             ASSISTANT 消息 ID
     * @param deliveryStatus 新状态（SENT/FAILED）
     * @param sentAt         发送成功时间，失败时为 null
     * @return 影响行数
     */
    int updateDeliveryStatus(@Param("id") Long id,
                             @Param("deliveryStatus") String deliveryStatus,
                             @Param("sentAt") LocalDateTime sentAt);
}
