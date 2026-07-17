-- LA-005: 一次性提醒创建、调整与到期发送
-- reminders：正式提醒领域表
-- scheduled_jobs：持久化调度任务，当前只支持 REMINDER_DELIVERY

-- 1. reminders：一次性提醒
CREATE TABLE IF NOT EXISTS reminders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    content         VARCHAR(1000) NOT NULL,
    event_at        TIMESTAMP WITH TIME ZONE,
    timezone        VARCHAR(50)  NOT NULL DEFAULT 'Asia/Shanghai',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    source_message_id BIGINT     NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  reminders IS '一次性提醒领域表';
COMMENT ON COLUMN reminders.user_id IS '所属用户，FK → users.id';
COMMENT ON COLUMN reminders.content IS '提醒事项，最长 1,000 字符';
COMMENT ON COLUMN reminders.event_at IS '事件发生时间（可空）';
COMMENT ON COLUMN reminders.timezone IS '用户 IANA 时区，P0 为 Asia/Shanghai';
COMMENT ON COLUMN reminders.status IS '状态：ACTIVE/DELIVERED/ACKNOWLEDGED/COMPLETED/CANCELLED/FAILED';
COMMENT ON COLUMN reminders.source_message_id IS '创建来源 USER 消息 ID（非空唯一）';
COMMENT ON COLUMN reminders.version IS '乐观锁版本';

CREATE UNIQUE INDEX IF NOT EXISTS uk_reminders_source_message
    ON reminders(source_message_id);

CREATE INDEX IF NOT EXISTS ix_reminders_user_id
    ON reminders(user_id);

-- 2. scheduled_jobs：持久化调度任务
CREATE TABLE IF NOT EXISTS scheduled_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    job_type            VARCHAR(30)  NOT NULL,
    business_key        VARCHAR(200) NOT NULL,
    reminder_id         BIGINT       NOT NULL REFERENCES reminders(id),
    node_type           VARCHAR(20)  NOT NULL,
    scheduled_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    next_run_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'READY',
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    lease_owner         VARCHAR(100),
    lease_until         TIMESTAMP WITH TIME ZONE,
    assistant_message_id BIGINT,
    last_error_code     VARCHAR(50),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  scheduled_jobs IS '持久化调度任务，当前只支持 REMINDER_DELIVERY';
COMMENT ON COLUMN scheduled_jobs.job_type IS '任务类型，当前只允许 REMINDER_DELIVERY';
COMMENT ON COLUMN scheduled_jobs.business_key IS '稳定唯一键，防止重复创建 Job';
COMMENT ON COLUMN scheduled_jobs.reminder_id IS 'FK → reminders.id';
COMMENT ON COLUMN scheduled_jobs.node_type IS '节点类型：PRIMARY/ADVANCE/SNOOZE';
COMMENT ON COLUMN scheduled_jobs.scheduled_at IS '原计划 UTC 时间，创建后不变';
COMMENT ON COLUMN scheduled_jobs.next_run_at IS '下一次领取时间，重试时更新';
COMMENT ON COLUMN scheduled_jobs.status IS '状态：READY/RUNNING/SUCCEEDED/RETRY_WAIT/FAILED/CANCELLED/MISSED';
COMMENT ON COLUMN scheduled_jobs.retry_count IS '已执行重试次数，最大 3';
COMMENT ON COLUMN scheduled_jobs.lease_owner IS '当前持有者标识';
COMMENT ON COLUMN scheduled_jobs.lease_until IS '租约截止时间';
COMMENT ON COLUMN scheduled_jobs.assistant_message_id IS '首次执行创建的幂等 ASSISTANT 消息 ID';
COMMENT ON COLUMN scheduled_jobs.last_error_code IS '最后一次失败错误码（脱敏）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_scheduled_jobs_business_key
    ON scheduled_jobs(business_key);

CREATE INDEX IF NOT EXISTS ix_scheduled_jobs_status_next_run
    ON scheduled_jobs(status, next_run_at);

CREATE INDEX IF NOT EXISTS ix_scheduled_jobs_reminder_status
    ON scheduled_jobs(reminder_id, status);

-- assistant_message_id 非空时唯一：同一提醒消息不能被多个 Job 复用
CREATE UNIQUE INDEX IF NOT EXISTS uk_scheduled_jobs_assistant_msg
    ON scheduled_jobs(assistant_message_id) WHERE assistant_message_id IS NOT NULL;
