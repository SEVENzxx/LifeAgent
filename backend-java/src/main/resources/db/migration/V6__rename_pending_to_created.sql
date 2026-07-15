-- LA-002: PENDING → CREATED 重命名
-- PENDING 对于用户消息容易误解为"等待投递"，实际用户消息已成功收到。
-- 统一改为 CREATED：用户消息表示"已创建/已收到"，助手消息表示"已创建待发送"。

ALTER TABLE conversation_messages
    ALTER COLUMN delivery_status SET DEFAULT 'CREATED';

COMMENT ON COLUMN conversation_messages.delivery_status IS '消息状态：CREATED、SENT、FAILED';
