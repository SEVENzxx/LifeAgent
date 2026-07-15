from functools import lru_cache
import hmac
import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, status

from lifeagent_ai.config import (
    INTERNAL_TOKEN_HEADER,
    ProviderType,
    Settings,
    get_settings,
)
from lifeagent_ai.providers.base import ModelProvider
from lifeagent_ai.providers.openai_compat import OpenAICompatProvider
from lifeagent_ai.schemas import HealthResponse, TurnResolutionRequest, TurnResolutionResponse
from lifeagent_ai.schemas.common import HealthStatus
from lifeagent_ai.services.turn_resolver import resolve_turn

router = APIRouter(prefix="/internal/v1")
logger = logging.getLogger(__name__)


@lru_cache()
def get_model_provider() -> ModelProvider:
    """创建并缓存无状态 ModelProvider，避免每次请求重复构造。"""
    settings = get_settings()
    if settings.llm_provider == ProviderType.OPENAI_COMPAT:
        return OpenAICompatProvider(settings)
    raise ValueError(f"不支持的 LLM Provider: {settings.llm_provider}")


def verify_internal_token(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_token: Annotated[
        str | None,
        Header(alias=INTERNAL_TOKEN_HEADER, description="Java 调用 AI 服务使用的内部鉴权令牌"),
    ] = None,
) -> None:
    token = x_internal_token or ""
    if not hmac.compare_digest(token, settings.internal_service_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="内部服务令牌无效",
        )


@router.get(
    "/health/live",
    response_model=HealthResponse,
    summary="检查 AI 服务存活状态",
    description="确认 FastAPI 进程已经启动，并返回当前配置的模型提供方。",
)
async def liveness(
    settings: Annotated[Settings, Depends(get_settings)],
) -> HealthResponse:
    return HealthResponse(status="UP", provider=settings.llm_provider)


@router.get(
    "/health/ready",
    response_model=HealthResponse,
    summary="检查 AI 服务就绪状态",
    description="校验当前模型 Provider 配置是否足以创建轮次解析服务。",
)
async def readiness(
    settings: Annotated[Settings, Depends(get_settings)],
) -> HealthResponse:
    OpenAICompatProvider(settings)
    return HealthResponse(status="UP", provider=settings.llm_provider)


@router.post(
    "/turns/resolve",
    response_model=TurnResolutionResponse,
    dependencies=[Depends(verify_internal_token)],
    summary="解析当前对话轮次",
    description="根据 Java 提供的裁剪上下文判断消息意图并返回经过 Pydantic 校验的结果。",
)
async def resolve_turn_handler(
    request: TurnResolutionRequest,
    provider: Annotated[ModelProvider, Depends(get_model_provider)],
) -> TurnResolutionResponse:
    logger.info(
        "轮次解析请求进入, requestId=%s, schemaVersion=%s, currentMessageLen=%s, recentCount=%s",
        request.request_id,
        request.schema_version,
        len(request.context.current_message),
        len(request.context.recent_messages),
    )
    response = await resolve_turn(provider, request)
    logger.info(
        "轮次解析处理完成, requestId=%s, resolutionStatus=%s, intent=%s, confidence=%s",
        request.request_id,
        response.resolution_status,
        response.intent,
        response.confidence,
    )
    return response
