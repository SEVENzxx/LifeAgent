package com.lifeagent.service;

import com.lifeagent.dto.InboundMessageCommand;
import com.lifeagent.dto.InboundMessageResponse;

/**
 * 会话服务，负责消息接收。
 *
 * <p>同一事务保存 USER 消息（CREATED），由调用方在事务提交后触发异步 AI 处理。
 * 幂等由 idempotency_key 数据库唯一约束保证。</p>
 */
public interface ConversationService {

    /**
     * 处理渠道中立的入站消息。
     *
     * <p>Redis SETNX 预检后，以数据库 idempotency_key 唯一约束为最终
     * 幂等保证。重复消息直接返回首次保存结果。</p>
     *
     * @param command 渠道中立的入站参数
     * @return 入站处理结果
     */
    InboundMessageResponse processInboundMessage(InboundMessageCommand command);
}
