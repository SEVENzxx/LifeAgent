package com.lifeagent.service;

import com.lifeagent.dto.InboundMessageCommand;
import com.lifeagent.dto.InboundMessageRequest;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.dto.OutboundMessageResponse;

import java.util.List;

/**
 * 会话服务，负责消息接收和出站查询。
 *
 * <p>同一事务保存 USER 和 ASSISTANT 消息。Mock 渠道 ASSISTANT 直接标记 SENT；
 * 真实渠道 ASSISTANT 标记 CREATED，由调用方在事务提交后触发异步发送。
 * 幂等由 idempotency_key 数据库唯一约束保证。</p>
 */
public interface ConversationService {

    /**
     * 接收一条入站消息（Mock 同步处理）。
     *
     * <p>保留向后兼容，内部委托给 {@link #processInboundMessage(InboundMessageCommand)}。</p>
     *
     * @param request Mock 入站消息请求
     * @return 入站处理结果，包含 reply 文本
     */
    InboundMessageResponse receiveInboundMessage(InboundMessageRequest request);

    /**
     * 处理渠道中立的入站消息。
     *
     * <p>Redis SETNX 预检后，以数据库 idempotency_key 唯一约束为最终
     * 幂等保证。重复消息直接返回首次保存的固定回复。
     * ASSISTANT 投递方式由 channel 决定：MOCK → SENT，其余 → CREATED。</p>
     *
     * @param command 渠道中立的入站参数
     * @return 入站处理结果
     */
    InboundMessageResponse processInboundMessage(InboundMessageCommand command);

    /**
     * 查询指定 Mock 用户已发送的回复。
     *
     * @param externalUserId Mock 外部用户 ID
     * @return 已发送回复列表，按 created_at 和 id 升序
     */
    List<OutboundMessageResponse> listSentReplies(String externalUserId);
}
