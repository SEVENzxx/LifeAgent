from typing import Annotated, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator

from lifeagent_ai.config import (
    CONTEXT_RECENT_MAX_CHARS,
    CONTEXT_RECENT_MAX_MESSAGES,
    CONTEXT_SUMMARY_MAX_CHARS,
    INTENT_REMINDER_CREATE,
    MAX_MESSAGE_LENGTH,
    MAX_IDENTIFIER_LENGTH,
    RESOLUTION_AI_UNAVAILABLE,
    RESOLUTION_RESOLVED,
    SCHEMA_VERSION_PATTERN,
    VALID_DRAFT_ACTIONS,
    VALID_DRAFT_TARGETS,
    VALID_INTENTS,
    VALID_RESOLUTION_STATUSES,
)

MessageText = Annotated[
    str,
    Field(min_length=1, max_length=MAX_MESSAGE_LENGTH),
]
IntentName = Annotated[
    str,
    Field(min_length=1, max_length=MAX_IDENTIFIER_LENGTH),
]
ReplyDraft = Annotated[
    str | None,
    Field(None, min_length=1, max_length=MAX_MESSAGE_LENGTH),
]
SummaryText = Annotated[
    str | None,
    Field(None, min_length=1, max_length=CONTEXT_SUMMARY_MAX_CHARS),
]


class ContextMessageItem(BaseModel):
    """近期消息或待摘要消息项。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    message_id: int = Field(description="消息 ID")
    role: str = Field(pattern=r"^(USER|ASSISTANT)$", description="角色")
    content: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH, description="消息正文")


class PendingReminderInfo(BaseModel):
    """最多一个未过期 Redis 提醒候选。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    draft_token: str = Field(description="草稿 token")
    content: str | None = Field(default=None, max_length=1000, description="候选事项")
    event_at: str | None = Field(default=None, description="事件 UTC 时间（ISO 8601 带偏移）")
    remind_at: str | None = Field(default=None, description="主提醒候选 UTC 时间（ISO 8601 带偏移）")
    advance_remind_at: str | None = Field(default=None, description="提前提醒 UTC 时间（ISO 8601 带偏移）")
    time_source: str = Field(description="时间来源：USER_EXPLICIT / AI_SUGGESTED / NONE")
    draft_status: str = Field(description="候选状态：COLLECTING / AWAITING_CONFIRMATION")
    target_reminder_id: int | None = Field(default=None, description="目标 Reminder ID")
    target_reminder_version: int | None = Field(default=None, description="目标 Reminder 版本")


class RecentReminderInfo(BaseModel):
    """最多一个最近明确 Reminder 的白名单事实。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    reminder_id: int = Field(description="Reminder ID")
    version: int = Field(description="版本号")
    status: str = Field(description="状态")
    content: str = Field(max_length=1000, description="事项")
    event_at: str | None = Field(default=None, description="事件 UTC 时间（ISO 8601 带偏移）")
    remind_at: str | None = Field(default=None, description="主提醒 UTC 时间（ISO 8601 带偏移）")
    last_sent_at: str | None = Field(default=None, description="最近发送时间（ISO 8601 带偏移）")


class ReminderResolution(BaseModel):
    """Python 返回的提醒候选解析结果。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    target: str = Field(description="目标类型：NEW / PENDING_DRAFT / RECENT_REMINDER")
    action: str = Field(
        description="操作类型：UPSERT_DRAFT / CONFIRM_DRAFT / CREATE / MODIFY / ACK / COMPLETE / SNOOZE / CANCEL",
    )
    content: str | None = Field(default=None, max_length=1000, description="候选事项")
    event_at: str | None = Field(default=None, description="事件 UTC 时间（ISO 8601 带偏移）")
    remind_at: str | None = Field(default=None, description="主提醒 UTC 时间（ISO 8601 带偏移）")
    advance_remind_at: str | None = Field(default=None, description="提前提醒 UTC 时间（ISO 8601 带偏移）")
    time_source: str = Field(description="时间来源：USER_EXPLICIT / AI_SUGGESTED / NONE")
    missing_fields: list[str] = Field(
        default_factory=list,
        description="缺失字段：CONTENT / REMIND_AT / SNOOZE_AT",
    )

    @model_validator(mode="after")
    def validate_reminder_resolution_fields(self) -> Self:
        """校验 target、action 和 time_source 枚举值。"""
        if self.target not in VALID_DRAFT_TARGETS:
            raise ValueError(f"未知 target: {self.target}")
        if self.action not in VALID_DRAFT_ACTIONS:
            raise ValueError(f"未知 action: {self.action}")
        return self


class ContextPackage(BaseModel):
    """Java 侧裁剪后发送给模型的轮次上下文。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    current_message: MessageText = Field(description="当前用户消息正文")
    memory_summary: str | None = Field(
        default=None, max_length=CONTEXT_SUMMARY_MAX_CHARS,
        description="已有滚动摘要",
    )
    recent_messages: list[ContextMessageItem] = Field(
        default_factory=list,
        max_length=CONTEXT_RECENT_MAX_MESSAGES,
        description="近期窗口消息，最多 12 条",
    )
    summary_requested: bool = Field(
        default=False,
        description="Java 是否要求本轮生成新摘要",
    )
    summary_messages: list[ContextMessageItem] = Field(
        default_factory=list,
        description="本批待摘要的旧消息（summary_requested 为 true 时才有值）",
    )
    reference_time: str | None = Field(
        default=None,
        description="Java 当前带偏移 ISO 8601 时间",
    )
    timezone: str | None = Field(
        default=None,
        description="用户 IANA 时区",
    )
    pending_reminder: PendingReminderInfo | None = Field(
        default=None,
        description="最多一个未过期 Redis 候选",
    )
    recent_reminder: RecentReminderInfo | None = Field(
        default=None,
        description="最多一个最近明确 Reminder",
    )


class TurnResolutionRequest(BaseModel):
    """Java 服务发起轮次解析时使用的跨服务请求协议。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    request_id: str = Field(
        min_length=1, max_length=MAX_IDENTIFIER_LENGTH,
        description="本次模型调用的稳定请求 ID",
    )
    schema_version: str = Field(
        default="1",
        pattern=SCHEMA_VERSION_PATTERN,
        description="跨服务协议版本",
    )
    context: ContextPackage = Field(description="Java 侧裁剪并提供的轮次上下文")


class TurnResolutionResponse(BaseModel):
    """AI 服务返回给 Java 的轮次解析协议。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    request_id: str = Field(
        min_length=1, max_length=MAX_IDENTIFIER_LENGTH,
        description="与请求保持一致的稳定请求 ID",
    )
    schema_version: str = Field(
        default="1",
        pattern=SCHEMA_VERSION_PATTERN,
        description="与请求保持一致的协议版本",
    )
    resolution_status: str = Field(
        description="解析状态：RESOLVED / NEEDS_CLARIFICATION / AI_UNAVAILABLE",
    )
    intent: str = Field(description="五种 Intent 之一")
    confidence: float = Field(ge=0, le=1, description="模型判断置信度")
    reply_draft: str | None = Field(
        default=None, min_length=1, max_length=MAX_MESSAGE_LENGTH,
        description="回复草稿；AI 不可用时为空",
    )
    updated_summary: str | None = Field(
        default=None, max_length=CONTEXT_SUMMARY_MAX_CHARS,
        description="仅摘要成功时返回，最长 1,500",
    )
    reminder_resolution: ReminderResolution | None = Field(
        default=None,
        description="LA-005 提醒候选解析结果",
    )

    @model_validator(mode="after")
    def validate_cross_field_constraints(self) -> Self:
        """校验跨字段约束。"""
        status = self.resolution_status
        intent = self.intent
        if status not in VALID_RESOLUTION_STATUSES:
            raise ValueError(f"未知 resolution_status: {status}")
        if intent not in VALID_INTENTS:
            raise ValueError(f"未知 intent: {intent}")

        if status == RESOLUTION_RESOLVED:
            if not self.reply_draft:
                raise ValueError("RESOLVED 状态要求 reply_draft 必填")

        if status == RESOLUTION_AI_UNAVAILABLE:
            if self.confidence != 0.0:
                raise ValueError("AI_UNAVAILABLE 状态要求 confidence 为 0")
            if self.reply_draft is not None:
                raise ValueError("AI_UNAVAILABLE 状态要求 reply_draft 为 null")
            if self.updated_summary is not None:
                raise ValueError("AI_UNAVAILABLE 状态要求 updated_summary 为 null")

        # LA-005 提醒约束：非提醒消息时 reminder_resolution 必须为空
        if intent != INTENT_REMINDER_CREATE and self.reminder_resolution is not None:
            raise ValueError("非 REMINDER_CREATE 意图时不允许包含 reminder_resolution")

        return self
