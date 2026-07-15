from dataclasses import dataclass
from enum import Enum
from functools import lru_cache
import os


APP_TITLE = "LifeAgent AI"
APP_DESCRIPTION = "LifeAgent 无状态 AI Sidecar 内部接口"
APP_VERSION = "0.1.0"

INTERNAL_TOKEN_HEADER = "X-Internal-Token"

ENV_LLM_PROVIDER = "LLM_PROVIDER"
ENV_OPENAI_COMPAT_API_KEY = "OPENAI_COMPAT_API_KEY"
ENV_OPENAI_COMPAT_BASE_URL = "OPENAI_COMPAT_BASE_URL"
ENV_OPENAI_COMPAT_MODEL = "OPENAI_COMPAT_MODEL"
ENV_INTERNAL_SERVICE_TOKEN = "INTERNAL_SERVICE_TOKEN"
ENV_REQUEST_TIMEOUT_SECONDS = "LLM_REQUEST_TIMEOUT_SECONDS"

DEFAULT_INTERNAL_SERVICE_TOKEN = "lifeagent-local-token"

SCHEMA_VERSION_PATTERN = r"^\d+$"
MAX_MESSAGE_LENGTH = 4_000
MAX_IDENTIFIER_LENGTH = 100
MAX_MODEL_TEMPERATURE = 2
MIN_CONFIDENCE = 0.6

# LA-004 五种 Intent
INTENT_SMALL_TALK = "SMALL_TALK"
INTENT_REMINDER_CREATE = "REMINDER_CREATE"
INTENT_HABIT_CREATE = "HABIT_CREATE"
INTENT_PLAN_CREATE = "PLAN_CREATE"
INTENT_ANALYSIS_REQUEST = "ANALYSIS_REQUEST"

# LA-004 Resolution status
RESOLUTION_RESOLVED = "RESOLVED"
RESOLUTION_NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION"
RESOLUTION_AI_UNAVAILABLE = "AI_UNAVAILABLE"

VALID_INTENTS = frozenset({
    INTENT_SMALL_TALK, INTENT_REMINDER_CREATE,
    INTENT_HABIT_CREATE, INTENT_PLAN_CREATE, INTENT_ANALYSIS_REQUEST,
})
VALID_RESOLUTION_STATUSES = frozenset({
    RESOLUTION_RESOLVED, RESOLUTION_NEEDS_CLARIFICATION, RESOLUTION_AI_UNAVAILABLE,
})

# 上下文常量
CONTEXT_RECENT_MAX_MESSAGES = 12
CONTEXT_RECENT_MAX_CHARS = 6000
CONTEXT_SUMMARY_TRIGGER_MESSAGES = 20
CONTEXT_SUMMARY_TRIGGER_CHARS = 8000
CONTEXT_SUMMARY_MAX_CHARS = 1500


class ProviderType(str, Enum):
    """应用支持的模型提供方。"""

    OPENAI_COMPAT = "openai-compat"

    def __str__(self) -> str:
        return self.value


def _parse_timeout_seconds(raw: str) -> float:
    """安全解析超时秒数，非数字值时回退到 15 秒。"""
    try:
        return float(raw)
    except ValueError:
        return 15.0


@dataclass(frozen=True, slots=True)
class Settings:
    """从环境变量加载的不可变应用配置。"""

    llm_provider: ProviderType
    openai_compat_api_key: str
    openai_compat_base_url: str
    openai_compat_model: str
    internal_service_token: str
    request_timeout_seconds: float


@lru_cache()
def get_settings() -> Settings:
    """读取环境变量并生成不可变的应用配置。"""
    return Settings(
        llm_provider=ProviderType(os.getenv(ENV_LLM_PROVIDER, "openai-compat").lower()),
        openai_compat_api_key=os.getenv(ENV_OPENAI_COMPAT_API_KEY, ""),
        openai_compat_base_url=os.getenv(ENV_OPENAI_COMPAT_BASE_URL, ""),
        openai_compat_model=os.getenv(ENV_OPENAI_COMPAT_MODEL, "qwen-turbo"),
        internal_service_token=os.getenv(ENV_INTERNAL_SERVICE_TOKEN, DEFAULT_INTERNAL_SERVICE_TOKEN),
        request_timeout_seconds=_parse_timeout_seconds(
            os.getenv(ENV_REQUEST_TIMEOUT_SECONDS, "15")
        ),
    )
