from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field


ResponseData = TypeVar("ResponseData")


class ApiResponse(BaseModel, Generic[ResponseData]):
    """Python 服务需要统一响应包装时使用的通用模型。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    success: bool = Field(description="请求是否处理成功")
    message: str = Field(description="面向调用方的处理结果说明")
    data: ResponseData | None = Field(default=None, description="经过 Schema 校验的响应数据")
