CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN system_settings.created_at IS
    '创建时间，按 Asia/Shanghai 时区写入；TIMESTAMP 字段本身不携带时区';
COMMENT ON COLUMN system_settings.updated_at IS
    '更新时间，按 Asia/Shanghai 时区写入；TIMESTAMP 字段本身不携带时区';

INSERT INTO system_settings (setting_key, setting_value)
VALUES ('schema_version', '1')
ON CONFLICT (setting_key) DO NOTHING;
