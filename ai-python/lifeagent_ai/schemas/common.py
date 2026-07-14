from enum import Enum


class HealthStatus(str, Enum):
    """服务对健康探针公开的状态。"""

    UP = "UP"

    def __str__(self) -> str:
        return self.value
