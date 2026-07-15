-- LA-004: LLM 对话上下文与 ASSISTANT 幂等约束
-- conversation_contexts：每用户一行的滚动摘要和最近语义状态
-- ASSISTANT 唯一索引：防止异步 AI 任务重复执行时插入多条 ASSISTANT

-- 1. conversation_contexts：滚动摘要和最近意图
CREATE TABLE IF NOT EXISTS conversation_contexts (
    user_id BIGINT NOT NULL PRIMARY KEY REFERENCES users(id),
    summary TEXT,
    summarized_through_message_id BIGINT,
    last_intent VARCHAR(20),
    last_confidence NUMERIC(5,4),
    last_resolved_message_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN conversation_contexts.user_id IS '用户 ID，每用户最多一行';
COMMENT ON COLUMN conversation_contexts.summary IS '滚动摘要，最长 1,500 字符';
COMMENT ON COLUMN conversation_contexts.summarized_through_message_id IS 'Java 控制的摘要截止消息 ID，不设外键';
COMMENT ON COLUMN conversation_contexts.last_intent IS '最近有效 Intent，只允许 LA-004 当前五种';
COMMENT ON COLUMN conversation_contexts.last_confidence IS '最近语义识别置信度，0～1';
COMMENT ON COLUMN conversation_contexts.last_resolved_message_id IS '最近有效语义识别对应的 USER 消息 ID，不设外键';

-- 2. ASSISTANT 幂等唯一约束
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_idempotency_assistant
    ON conversation_messages(idempotency_key) WHERE role = 'ASSISTANT';
