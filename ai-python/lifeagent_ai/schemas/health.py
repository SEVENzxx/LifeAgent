from pydantic import BaseModel, ConfigDict, Field

from lifeagent_ai.config import ProviderType
from lifeagent_ai.schemas.common import HealthStatus


class HealthResponse(BaseModel):
    """AI 服务健康检查响应。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    status: HealthStatus = Field(description="服务健康状态")
    provider: ProviderType = Field(description="当前启用的模型提供方")
