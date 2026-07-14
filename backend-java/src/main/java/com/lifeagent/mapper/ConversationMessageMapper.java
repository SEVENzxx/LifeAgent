package com.lifeagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifeagent.entity.ConversationMessageEntity;

public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    /**
     * 使用 PostgreSQL ON CONFLICT DO NOTHING 安全插入 USER 消息。
     * 返回 1 表示插入成功，0 表示唯一键冲突（重复消息）。
     * SQL 定义在 ConversationMessageMapper.xml。
     */
    int insertIgnore(ConversationMessageEntity entity);
}
