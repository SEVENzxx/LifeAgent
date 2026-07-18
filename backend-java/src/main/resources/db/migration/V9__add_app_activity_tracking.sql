-- LA-006: App 行为事件上传、区间化与窗口查询
-- device_bindings：设备凭证和可撤销状态
-- monitored_apps：用户级已监测 App 登记
-- behavior_events：不可变原始事件
-- activity_intervals：从原始事件确定性重建的活动区间

-- 1. device_bindings：设备凭证
CREATE TABLE IF NOT EXISTS device_bindings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    device_id       VARCHAR(100)  NOT NULL,
    display_name    VARCHAR(200)  NOT NULL DEFAULT '',
    source_type     VARCHAR(20)   NOT NULL DEFAULT 'SHORTCUT',
    credential_hash VARCHAR(64)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  device_bindings IS '设备凭证和可撤销状态';
COMMENT ON COLUMN device_bindings.user_id IS '所属用户，FK → users.id';
COMMENT ON COLUMN device_bindings.device_id IS '稳定外部设备键，非空唯一';
COMMENT ON COLUMN device_bindings.display_name IS '设备显示名';
COMMENT ON COLUMN device_bindings.source_type IS '数据来源：SHORTCUT';
COMMENT ON COLUMN device_bindings.credential_hash IS 'Token SHA-256 摘要，非空';
COMMENT ON COLUMN device_bindings.status IS '设备状态：ACTIVE/REVOKED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_bindings_device_id
    ON device_bindings(device_id);

CREATE INDEX IF NOT EXISTS ix_device_bindings_user_id
    ON device_bindings(user_id);

-- 2. monitored_apps：用户级已监测 App
CREATE TABLE IF NOT EXISTS monitored_apps (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    app_key         VARCHAR(100)  NOT NULL,
    display_name    VARCHAR(100)  NOT NULL DEFAULT '',
    first_seen_at   TIMESTAMP WITH TIME ZONE,
    last_seen_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  monitored_apps IS '用户级已监测 App 登记';
COMMENT ON COLUMN monitored_apps.user_id IS '所属用户，FK → users.id';
COMMENT ON COLUMN monitored_apps.app_key IS '稳定 App 标识，小写字母数字点下划线短横';
COMMENT ON COLUMN monitored_apps.display_name IS '最近一次成功事件提供的显示名';
COMMENT ON COLUMN monitored_apps.first_seen_at IS '首次事件时间';
COMMENT ON COLUMN monitored_apps.last_seen_at IS '最近事件时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_monitored_apps_user_app
    ON monitored_apps(user_id, app_key);

-- 3. behavior_events：不可变原始事件
CREATE TABLE IF NOT EXISTS behavior_events (
    id                  BIGSERIAL PRIMARY KEY,
    device_binding_id   BIGINT        NOT NULL REFERENCES device_bindings(id),
    monitored_app_id    BIGINT        NOT NULL REFERENCES monitored_apps(id),
    event_id            VARCHAR(36)   NOT NULL,
    event_type          VARCHAR(10)   NOT NULL,
    event_time          TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_version      VARCHAR(50)   NOT NULL,
    payload_hash        VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  behavior_events IS '不可变原始事件，插入后不可更新或删除';
COMMENT ON COLUMN behavior_events.device_binding_id IS 'FK → device_bindings.id';
COMMENT ON COLUMN behavior_events.monitored_app_id IS 'FK → monitored_apps.id';
COMMENT ON COLUMN behavior_events.event_id IS '客户端生成 UUID';
COMMENT ON COLUMN behavior_events.event_type IS '事件类型：OPEN/CLOSE';
COMMENT ON COLUMN behavior_events.event_time IS '设备观察时间';
COMMENT ON COLUMN behavior_events.received_at IS '服务端接收时间';
COMMENT ON COLUMN behavior_events.client_version IS '客户端版本标识';
COMMENT ON COLUMN behavior_events.payload_hash IS '规范化正文 SHA-256，用于冲突检测';

-- 同一设备 event_id 唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_behavior_events_device_event
    ON behavior_events(device_binding_id, event_id);

-- 按设备时间顺序查询事件
CREATE INDEX IF NOT EXISTS ix_behavior_events_device_time
    ON behavior_events(device_binding_id, event_time, id);

-- 按 monitored_app 时间顺序查询
CREATE INDEX IF NOT EXISTS ix_behavior_events_app_time
    ON behavior_events(monitored_app_id, event_time, id);

-- 4. activity_intervals：从原始事件重建的活动区间
CREATE TABLE IF NOT EXISTS activity_intervals (
    id                  BIGSERIAL PRIMARY KEY,
    device_binding_id   BIGINT        NOT NULL REFERENCES device_bindings(id),
    monitored_app_id    BIGINT        NOT NULL REFERENCES monitored_apps(id),
    start_event_id      BIGINT        NOT NULL REFERENCES behavior_events(id),
    end_event_id        BIGINT        REFERENCES behavior_events(id),
    start_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at              TIMESTAMP WITH TIME ZONE,
    quality             VARCHAR(20)   NOT NULL,
    end_reason          VARCHAR(20)   NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  activity_intervals IS '从原始事件确定性重建的活动区间';
COMMENT ON COLUMN activity_intervals.device_binding_id IS 'FK → device_bindings.id';
COMMENT ON COLUMN activity_intervals.monitored_app_id IS 'FK → monitored_apps.id';
COMMENT ON COLUMN activity_intervals.start_event_id IS 'FK → behavior_events.id，唯一';
COMMENT ON COLUMN activity_intervals.end_event_id IS 'FK → behavior_events.id，可空';
COMMENT ON COLUMN activity_intervals.start_at IS '区间开始时间';
COMMENT ON COLUMN activity_intervals.end_at IS '区间结束时间，可空';
COMMENT ON COLUMN activity_intervals.quality IS 'EXACT/INFERRED_SWITCH/TRUNCATED/OPEN';
COMMENT ON COLUMN activity_intervals.end_reason IS 'EXPLICIT_CLOSE/APP_SWITCH/MAX_DURATION/STILL_OPEN';

-- 每个 OPEN 事件最多一个派生区间
CREATE UNIQUE INDEX IF NOT EXISTS uk_activity_intervals_start_event
    ON activity_intervals(start_event_id);

-- 每台设备最多一个开放区间
CREATE UNIQUE INDEX IF NOT EXISTS uk_activity_intervals_device_open
    ON activity_intervals(device_binding_id) WHERE end_at IS NULL;

-- 按设备时间查询区间
CREATE INDEX IF NOT EXISTS ix_activity_intervals_device_time
    ON activity_intervals(device_binding_id, start_at);

-- 按 App 时间查询区间
CREATE INDEX IF NOT EXISTS ix_activity_intervals_app_time
    ON activity_intervals(monitored_app_id, start_at);

-- 区间重叠查询条件：start_at < to AND COALESCE(end_at, cap) > from
CREATE INDEX IF NOT EXISTS ix_activity_intervals_device_overlap
    ON activity_intervals(device_binding_id, start_at, end_at);
