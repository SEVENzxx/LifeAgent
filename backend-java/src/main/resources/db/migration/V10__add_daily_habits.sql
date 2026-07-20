-- LA-007: 日常习惯创建、确认与调度
-- habits：日常习惯正式表，只保存已确认规则
-- habit_executions：习惯执行记录，每个提醒时刻一条
-- scheduled_jobs：扩展支持 HABIT_DELIVERY

-- 1. habits：日常习惯
CREATE TABLE IF NOT EXISTS habits (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    daily_times     JSONB        NOT NULL,
    timezone        VARCHAR(50)  NOT NULL DEFAULT 'Asia/Shanghai',
    start_date      DATE         NOT NULL,
    end_date        DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    source_message_id BIGINT     NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  habits IS '日常习惯正式表，只保存已确认规则';
COMMENT ON COLUMN habits.user_id IS '所属用户，FK → users.id';
COMMENT ON COLUMN habits.name IS '习惯名称，最长 200 字符';
COMMENT ON COLUMN habits.daily_times IS 'JSONB 字符串数组，1～10 个已排序去重 HH:mm';
COMMENT ON COLUMN habits.timezone IS '用户 IANA 时区，P0 为 Asia/Shanghai';
COMMENT ON COLUMN habits.start_date IS '用户本地开始日期';
COMMENT ON COLUMN habits.end_date IS '可空，不得早于开始日期';
COMMENT ON COLUMN habits.status IS '状态：ACTIVE/PAUSED/CANCELLED/ENDED';
COMMENT ON COLUMN habits.source_message_id IS '确认创建的 USER 消息 ID，非空唯一';
COMMENT ON COLUMN habits.version IS '乐观锁版本';

CREATE UNIQUE INDEX IF NOT EXISTS uk_habits_source_message
    ON habits(source_message_id);

CREATE INDEX IF NOT EXISTS ix_habits_user_id
    ON habits(user_id);

CREATE INDEX IF NOT EXISTS ix_habits_status
    ON habits(status);

-- 2. habit_executions：习惯执行记录
CREATE TABLE IF NOT EXISTS habit_executions (
    id                  BIGSERIAL PRIMARY KEY,
    habit_id            BIGINT       NOT NULL REFERENCES habits(id),
    occurrence_key      VARCHAR(30)  NOT NULL,
    scheduled_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    completed_at        TIMESTAMP WITH TIME ZONE,
    completion_message_id BIGINT,
    version             INTEGER      NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  habit_executions IS '习惯执行记录，每个提醒时刻一条';
COMMENT ON COLUMN habit_executions.habit_id IS 'FK → habits.id';
COMMENT ON COLUMN habit_executions.occurrence_key IS '本地日期＋时刻稳定键，例如 2026-07-19#09:00';
COMMENT ON COLUMN habit_executions.scheduled_at IS '转换后的 UTC 时刻';
COMMENT ON COLUMN habit_executions.status IS 'SCHEDULED/DELIVERED/ACKNOWLEDGED/COMPLETED/FAILED/MISSED/CANCELLED';
COMMENT ON COLUMN habit_executions.completed_at IS '用户确认完成时间，可空';
COMMENT ON COLUMN habit_executions.completion_message_id IS '完成来源 USER 消息 ID，可空且非空时唯一';

CREATE UNIQUE INDEX IF NOT EXISTS uk_habit_executions_occurrence
    ON habit_executions(habit_id, occurrence_key);

CREATE INDEX IF NOT EXISTS ix_habit_executions_habit_id
    ON habit_executions(habit_id, status);

-- completion_message_id 非空时唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_habit_executions_completion_msg
    ON habit_executions(completion_message_id) WHERE completion_message_id IS NOT NULL;

-- 3. 扩展 scheduled_jobs：支持 HABIT_DELIVERY
-- reminder_id 设为可空
ALTER TABLE scheduled_jobs ALTER COLUMN reminder_id DROP NOT NULL;

-- 增加 habit_execution_id
ALTER TABLE scheduled_jobs
    ADD COLUMN IF NOT EXISTS habit_execution_id BIGINT REFERENCES habit_executions(id);

COMMENT ON COLUMN scheduled_jobs.habit_execution_id IS 'FK → habit_executions.id，HABIT_DELIVERY 时非空';

-- 每个 habit_execution 最多一个投递 Job
CREATE UNIQUE INDEX IF NOT EXISTS uk_scheduled_jobs_habit_execution
    ON scheduled_jobs(habit_execution_id) WHERE habit_execution_id IS NOT NULL;

-- 更新 job_type 注释
COMMENT ON COLUMN scheduled_jobs.job_type IS '任务类型：REMINDER_DELIVERY / HABIT_DELIVERY';

-- 更新业务键索引覆盖范围
COMMENT ON COLUMN scheduled_jobs.business_key IS 'REMINDER_DELIVERY:{reminderId}:{nodeType} 或 HABIT_DELIVERY:{habitId}:{occurrenceKey}';
