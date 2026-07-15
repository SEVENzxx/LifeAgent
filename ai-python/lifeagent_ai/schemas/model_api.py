from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from lifeagent_ai.config import MAX_MODEL_TEMPERATURE


class ChatMessage(BaseModel):
    """发送给模型厂商的单条结构化消息。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    role: Literal["system", "user", "assistant"] = Field(description="模型消息角色")
    content: str = Field(min_length=1, description="发送给模型的消息内容")


class ChatCompletionRequest(BaseModel):
    """调用 OpenAI 兼容接口的对话补全请求协议。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    model: str = Field(min_length=1, description="模型名称")
    messages: list[ChatMessage] = Field(min_length=1, description="发送给模型的结构化消息列表")
    response_format: dict = Field(
        default_factory=lambda: {"type": "json_object"},
        description="要求模型返回 JSON 对象",
    )
    temperature: float = Field(
        default=0, ge=0, le=MAX_MODEL_TEMPERATURE,
        description="模型采样温度",
    )


class ChatCompletionMessage(BaseModel):
    """模型候选结果中的消息正文。"""

    model_config = ConfigDict(extra="ignore", frozen=True)

    content: str = Field(min_length=1, description="模型返回的文本")


class ChatCompletionChoice(BaseModel):
    """模型返回的单个候选结果。"""

    model_config = ConfigDict(extra="ignore", frozen=True)

    message: ChatCompletionMessage = Field(description="本次候选结果中的模型消息")


class ChatCompletionResponse(BaseModel):
    """业务层需要解析的模型响应字段。"""

    model_config = ConfigDict(extra="ignore", frozen=True)

    choices: list[ChatCompletionChoice] = Field(min_length=1, description="模型返回的候选结果列表")
