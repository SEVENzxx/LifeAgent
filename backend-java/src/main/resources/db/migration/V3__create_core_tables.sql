-- LA-002: 核心表（三表同步闭环）
-- 无 Worker/Job/Outbox/Turn/Thread。
-- 幂等由 idempotency_key 唯一约束保证。
-- 消息顺序由 (id, created_at) 确定。
-- 同步流程：接收 → 保存 USER → 保存 ASSISTANT(SENT) → 返回。

-- 1. users：单用户模式，始终使用第一条记录
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN users.status IS '用户状态：ACTIVE';
COMMENT ON COLUMN users.timezone IS 'IANA 时区，P0 固定 Asia/Shanghai';

-- 2. channel_bindings：外部渠道用户绑定
CREATE TABLE IF NOT EXISTS channel_bindings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    channel VARCHAR(20) NOT NULL,
    external_user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_channel_binding UNIQUE (channel, external_user_id)
);

COMMENT ON COLUMN channel_bindings.user_id IS '绑定的内部用户 ID';
COMMENT ON COLUMN channel_bindings.channel IS '渠道标识，如 MOCK、WECOM';
COMMENT ON COLUMN channel_bindings.external_user_id IS '外部渠道的用户标识';

CREATE INDEX IF NOT EXISTS idx_channel_bindings_user_id ON channel_bindings(user_id);

-- 3. conversation_messages：USER 和 ASSISTANT 消息
-- idempotency_key = channel + ":" + externalMessageId，USER 消息唯一。
-- 顺序由 id 和 created_at 保证，不需要 turnNo 或 Thread 中间表。
CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    channel_binding_id BIGINT NOT NULL REFERENCES channel_bindings(id),
    idempotency_key VARCHAR(200) NOT NULL,
    external_message_id VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN conversation_messages.channel_binding_id IS '所属渠道绑定';
COMMENT ON COLUMN conversation_messages.idempotency_key IS '全局幂等键 = channel + ":" + externalMessageId';
COMMENT ON COLUMN conversation_messages.external_message_id IS '外部消息 ID，仅作参考';
COMMENT ON COLUMN conversation_messages.role IS '角色：USER、ASSISTANT';
COMMENT ON COLUMN conversation_messages.content IS '消息正文';
COMMENT ON COLUMN conversation_messages.content_hash IS '内容哈希';
COMMENT ON COLUMN conversation_messages.delivery_status IS '投递状态：PENDING、SENT';

-- USER 消息幂等唯一约束（核心幂等保证）
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_idempotency_user
    ON conversation_messages(idempotency_key) WHERE role = 'USER';
-- 按 binding 查询已发送回复
CREATE INDEX IF NOT EXISTS idx_messages_binding_delivery
    ON conversation_messages(channel_binding_id, role, delivery_status);
-- 按幂等键快速查询（查找重复时的 ASSISTANT 回复）
CREATE INDEX IF NOT EXISTS idx_messages_idempotency_role
    ON conversation_messages(idempotency_key, role);
