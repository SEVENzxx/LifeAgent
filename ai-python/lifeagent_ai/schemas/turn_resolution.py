from typing import Annotated, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator

from lifeagent_ai.config import (
    CONTEXT_RECENT_MAX_CHARS,
    CONTEXT_RECENT_MAX_MESSAGES,
    CONTEXT_SUMMARY_MAX_CHARS,
    MAX_MESSAGE_LENGTH,
    MAX_IDENTIFIER_LENGTH,
    RESOLUTION_AI_UNAVAILABLE,
    RESOLUTION_RESOLVED,
    SCHEMA_VERSION_PATTERN,
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

        return self
