from dataclasses import dataclass
from enum import Enum
from functools import lru_cache
import os


APP_TITLE: str = "LifeAgent AI"
APP_DESCRIPTION: str = "LifeAgent 无状态 AI Sidecar 内部接口"
APP_VERSION: str = "0.1.0"

API_PREFIX: str = "/internal/v1"
LIVENESS_PATH: str = "/health/live"
READINESS_PATH: str = "/health/ready"
TURN_RESOLUTION_PATH: str = "/turns/resolve"
INTERNAL_TOKEN_HEADER: str = "X-Internal-Token"
AUTHORIZATION_HEADER: str = "Authorization"
BEARER_PREFIX: str = "Bearer"
DEEPSEEK_CHAT_COMPLETIONS_PATH: str = "chat/completions"

ENV_LLM_PROVIDER: str = "LLM_PROVIDER"
ENV_DEEPSEEK_API_KEY: str = "DEEPSEEK_API_KEY"
ENV_DEEPSEEK_BASE_URL: str = "DEEPSEEK_BASE_URL"
ENV_DEEPSEEK_MODEL: str = "DEEPSEEK_MODEL"
ENV_INTERNAL_SERVICE_TOKEN: str = "INTERNAL_SERVICE_TOKEN"
ENV_REQUEST_TIMEOUT_SECONDS: str = "LLM_REQUEST_TIMEOUT_SECONDS"

DEFAULT_LLM_PROVIDER: str = "mock"
DEFAULT_DEEPSEEK_BASE_URL: str = "https://api.deepseek.com/v1"
DEFAULT_DEEPSEEK_MODEL: str = "deepseek-chat"
DEFAULT_INTERNAL_SERVICE_TOKEN: str = "lifeagent-local-token"
DEFAULT_REQUEST_TIMEOUT_SECONDS: str = "15"

DEFAULT_SCHEMA_VERSION: str = "1"
SCHEMA_VERSION_PATTERN: str = r"^\d+$"
MAX_MESSAGE_LENGTH: int = 4_000
MAX_IDENTIFIER_LENGTH: int = 100
MAX_OPEN_FLOW_COUNT: int = 3
MAX_MODEL_TEMPERATURE: int = 2
MIN_CONFIDENCE: float = 0.6
MILLISECONDS_PER_SECOND: int = 1_000

UNKNOWN_INTENT: str = "UNKNOWN"
UNKNOWN_REPLY: str = "我还不能确定你的意思，请补充说明。"
PROVIDER_FALLBACK_REPLY: str = "模型服务暂时不可用，请稍后重试。"
MODEL_INVOKE_ERROR_MESSAGE: str = "模型服务暂时不可用"
INVALID_INTERNAL_TOKEN_MESSAGE: str = "内部服务令牌无效"
DEEPSEEK_API_KEY_REQUIRED_MESSAGE: str = "使用 DeepSeek Provider 时必须配置 {}"
MODEL_REQUEST_ID_MISMATCH_MESSAGE: str = "模型返回的 request_id 与请求不一致"
MODEL_SCHEMA_VERSION_MISMATCH_MESSAGE: str = "模型返回的 schema_version 与请求不一致"
MODEL_TARGET_FLOW_NOT_OPEN_MESSAGE: str = "模型返回的 target_flow_id 不在开放流程中"
TARGET_FLOW_REQUIRED_MESSAGE: str = "回答或确认已有流程时必须返回 target_flow_id"
TARGET_FLOW_FORBIDDEN_MESSAGE: str = "未关联已有流程时不能返回 target_flow_id"
UNSUPPORTED_PROVIDER_MESSAGE: str = "不支持的 LLM Provider: {}"

MOCK_MODEL_NAME: str = "deterministic-mock"
MOCK_SMALL_TALK_WORDS: tuple[str, ...] = ("你好", "谢谢", "再见")
SMALL_TALK_INTENT: str = "SMALL_TALK"
UNCLASSIFIED_INTENT: str = "UNCLASSIFIED"
MOCK_SMALL_TALK_REPLY: str = "你好，我已经准备好了。"
MOCK_DEFAULT_REPLY: str = "我已收到，后续会根据业务规则继续处理。"

MODEL_SYSTEM_ROLE: str = "system"
MODEL_USER_ROLE: str = "user"


class ProviderType(str, Enum):
    """应用支持的模型提供方。"""

    DEEPSEEK = "deepseek"
    MOCK = "mock"

    def __str__(self) -> str:
        return self.value


def _parse_timeout_seconds(raw: str) -> float:
    """安全解析超时秒数，非数字值时回退到默认值。"""
    try:
        return float(raw)
    except ValueError:
        return float(DEFAULT_REQUEST_TIMEOUT_SECONDS)


@dataclass(frozen=True, slots=True)
class Settings:
    """从环境变量加载的不可变应用配置。"""

    llm_provider: ProviderType
    deepseek_api_key: str
    deepseek_base_url: str
    deepseek_model: str
    internal_service_token: str
    request_timeout_seconds: float


@lru_cache()
def get_settings() -> Settings:
    """读取环境变量并生成不可变的应用配置。"""
    return Settings(
        llm_provider=ProviderType(os.getenv(ENV_LLM_PROVIDER, DEFAULT_LLM_PROVIDER).lower()),
        deepseek_api_key=os.getenv(ENV_DEEPSEEK_API_KEY, ""),
        deepseek_base_url=os.getenv(ENV_DEEPSEEK_BASE_URL, DEFAULT_DEEPSEEK_BASE_URL),
        deepseek_model=os.getenv(ENV_DEEPSEEK_MODEL, DEFAULT_DEEPSEEK_MODEL),
        internal_service_token=os.getenv(ENV_INTERNAL_SERVICE_TOKEN, DEFAULT_INTERNAL_SERVICE_TOKEN),
        request_timeout_seconds=_parse_timeout_seconds(
            os.getenv(ENV_REQUEST_TIMEOUT_SECONDS, DEFAULT_REQUEST_TIMEOUT_SECONDS)
        ),
    )
