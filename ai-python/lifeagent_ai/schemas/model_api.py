from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from lifeagent_ai.config import MAX_MODEL_TEMPERATURE


class ChatMessage(BaseModel):
    """发送给模型厂商的单条结构化消息。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    role: Literal["system", "user", "assistant"] = Field(description="模型消息角色")
    content: str = Field(min_length=1, description="发送给模型的消息内容")


class JsonResponseFormat(BaseModel):
    """模型厂商支持的 JSON 响应格式约束。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    type: Literal["json_object"] = Field(default="json_object", description="要求模型返回 JSON 对象")


class DeepSeekChatRequest(BaseModel):
    """调用 DeepSeek 对话补全接口的请求协议。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    model: str = Field(min_length=1, description="DeepSeek 模型名称")
    messages: list[ChatMessage] = Field(min_length=1, description="发送给模型的结构化消息列表")
    response_format: JsonResponseFormat = Field(
        default_factory=JsonResponseFormat,
        description="模型响应格式约束",
    )
    temperature: float = Field(
        default=0,
        ge=0,
        le=MAX_MODEL_TEMPERATURE,
        description="模型采样温度",
    )


class DeepSeekMessage(BaseModel):
    """DeepSeek 候选结果中的消息正文。"""

    model_config = ConfigDict(extra="ignore", frozen=True)

    content: str = Field(min_length=1, description="DeepSeek 返回的模型文本")


class DeepSeekChoice(BaseModel):
    """DeepSeek 返回的单个候选结果。"""

    model_config = ConfigDict(extra="ignore", frozen=True)

    message: DeepSeekMessage = Field(description="本次候选结果中的模型消息")


class DeepSeekChatResponse(BaseModel):
    """业务层需要解析的 DeepSeek 响应字段。"""

    # 模型厂商可能增加统计字段，只提取协议校验需要的 choices，避免原始对象进入业务层。
    model_config = ConfigDict(extra="ignore", frozen=True)

    choices: list[DeepSeekChoice] = Field(min_length=1, description="DeepSeek 返回的候选结果列表")
