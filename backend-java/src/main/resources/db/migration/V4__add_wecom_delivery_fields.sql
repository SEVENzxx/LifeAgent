-- LA-003: 企业微信接入扩展字段
-- 只在 conversation_messages 增加 sent_at 字段

-- 1. 新增 sent_at 字段
ALTER TABLE conversation_messages
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP WITHOUT TIME ZONE;

COMMENT ON COLUMN conversation_messages.sent_at IS '消息发送时间：USER 来自渠道声明，ASSISTANT 只在渠道确认成功后写入';

-- 2. 回填历史 ASSISTANT(sent) sent_at = created_at（历史逻辑发送时间）
UPDATE conversation_messages
SET sent_at = created_at
WHERE role = 'ASSISTANT' AND delivery_status = 'SENT' AND sent_at IS NULL;

-- 3. 检查约束：ASSISTANT SENT 时 sent_at 必须非空，非 SENT 时 sent_at 必须为空
ALTER TABLE conversation_messages
    DROP CONSTRAINT IF EXISTS ck_assistant_sent_at;

ALTER TABLE conversation_messages
    ADD CONSTRAINT ck_assistant_sent_at
    CHECK (
        role <> 'ASSISTANT'
        OR ((delivery_status = 'SENT') = (sent_at IS NOT NULL))
    );
