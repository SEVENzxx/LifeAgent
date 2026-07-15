from abc import ABC, abstractmethod

from lifeagent_ai.schemas import TurnResolutionRequest, TurnResolutionResponse


class ModelInvokeError(Exception):
    """模型厂商调用失败或模型输出未通过协议校验。"""


class ModelProvider(ABC):
    """统一隔离模型厂商调用，并只返回经过协议校验的结果。"""

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """返回日志和指标使用的模型提供方名称。"""
        ...

    @property
    @abstractmethod
    def model_name(self) -> str:
        """返回当前实际调用的模型名称。"""
        ...

    @abstractmethod
    async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
        """解析当前轮次；厂商调用或输出校验失败时抛出 ModelInvokeError。"""
        ...
