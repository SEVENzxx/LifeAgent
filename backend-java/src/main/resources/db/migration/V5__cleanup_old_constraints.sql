-- 清理旧版 V4 遗留约束和字段
-- message_type 已不再使用，ck_message_type_role 会阻止新 ASSISTANT 插入

ALTER TABLE conversation_messages
    DROP CONSTRAINT IF EXISTS ck_message_type_role;
