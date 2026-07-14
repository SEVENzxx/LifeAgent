from enum import Enum
from typing import Annotated, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator, StringConstraints

from lifeagent_ai.config import (
    DEFAULT_SCHEMA_VERSION,
    MAX_IDENTIFIER_LENGTH,
    MAX_MESSAGE_LENGTH,
    MAX_OPEN_FLOW_COUNT,
    SCHEMA_VERSION_PATTERN,
    TARGET_FLOW_FORBIDDEN_MESSAGE,
    TARGET_FLOW_REQUIRED_MESSAGE,
)


MessageText = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_MESSAGE_LENGTH),
]
FlowId = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_IDENTIFIER_LENGTH),
]
IntentName = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_IDENTIFIER_LENGTH),
]
ReplyDraft = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=MAX_MESSAGE_LENGTH),
]


class RelationType(str, Enum):
    """当前消息与开放流程之间的关系。"""

    ANSWER_FLOW = "ANSWER_FLOW"
    CONFIRM_FLOW = "CONFIRM_FLOW"
    NEW_INTENT = "NEW_INTENT"
    SMALL_TALK = "SMALL_TALK"
    UNKNOWN = "UNKNOWN"

    def __str__(self) -> str:
        return self.value

    @property
    def requires_target_flow(self) -> bool:
        """返回当前关系是否必须绑定一个开放流程。"""
        return self in (RelationType.ANSWER_FLOW, RelationType.CONFIRM_FLOW)


class ContextPackage(BaseModel):
    """Java 侧裁剪后发送给模型的轮次上下文。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    current_message: MessageText = Field(description="当前用户消息正文")
    open_flow_ids: list[FlowId] = Field(
        default_factory=list,
        max_length=MAX_OPEN_FLOW_COUNT,
        description="当前可继续处理的开放流程 ID，最多三个",
    )


class TurnResolutionRequest(BaseModel):
    """Java 服务发起轮次解析时使用的跨服务请求协议。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    request_id: FlowId = Field(description="本次模型调用的稳定请求 ID")
    schema_version: str = Field(
        default=DEFAULT_SCHEMA_VERSION,
        pattern=SCHEMA_VERSION_PATTERN,
        description="跨服务协议版本",
    )
    context: ContextPackage = Field(description="Java 侧裁剪并提供的轮次上下文")


class TurnResolutionResponse(BaseModel):
    """AI 服务返回给 Java 的轮次解析协议。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    request_id: FlowId = Field(description="与请求保持一致的稳定请求 ID")
    schema_version: str = Field(
        default=DEFAULT_SCHEMA_VERSION,
        pattern=SCHEMA_VERSION_PATTERN,
        description="与请求保持一致的协议版本",
    )
    relation: RelationType = Field(description="当前消息与开放流程的关系")
    target_flow_id: FlowId | None = Field(default=None, description="命中的开放流程 ID；未命中时为空")
    intent: IntentName = Field(description="经过结构化校验的意图名称")
    confidence: float = Field(ge=0, le=1, description="模型判断置信度，范围为 0.0 到 1.0")
    reply_draft: ReplyDraft = Field(description="供 Java 侧审核和发送的回复草稿")

    @model_validator(mode="after")
    def validate_target_flow_relation(self) -> Self:
        """校验关系类型与目标流程是否匹配，防止模型输出自相矛盾。"""
        # 该约束属于 Java/Python 跨服务协议，必须在模型输出进入业务层前统一执行。
        if self.relation.requires_target_flow and self.target_flow_id is None:
            raise ValueError(TARGET_FLOW_REQUIRED_MESSAGE)
        if not self.relation.requires_target_flow and self.target_flow_id is not None:
            raise ValueError(TARGET_FLOW_FORBIDDEN_MESSAGE)
        return self
