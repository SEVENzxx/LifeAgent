from functools import lru_cache
import hmac
import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, status

from lifeagent_ai.config import (
    API_PREFIX,
    INTERNAL_TOKEN_HEADER,
    INVALID_INTERNAL_TOKEN_MESSAGE,
    LIVENESS_PATH,
    READINESS_PATH,
    Settings,
    TURN_RESOLUTION_PATH,
    get_settings,
)
from lifeagent_ai.schemas import HealthResponse, HealthStatus, TurnResolutionRequest, TurnResolutionResponse
from lifeagent_ai.services import TurnResolverService, create_turn_resolver_service

router = APIRouter(prefix=API_PREFIX)
logger = logging.getLogger(__name__)


@lru_cache()
def get_turn_resolver_service() -> TurnResolverService:
    """创建并缓存无状态轮次解析 Service，避免每次请求重复构造 Provider。"""
    return create_turn_resolver_service(get_settings())


def verify_internal_token(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_token: Annotated[
        str | None,
        Header(alias=INTERNAL_TOKEN_HEADER, description="Java 调用 AI 服务使用的内部鉴权令牌"),
    ] = None,
) -> None:
    """使用常量时间比较校验内部令牌，避免普通字符串比较泄露时序信息。"""
    token = x_internal_token or ""
    if not hmac.compare_digest(token, settings.internal_service_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=INVALID_INTERNAL_TOKEN_MESSAGE,
        )


@router.get(
    LIVENESS_PATH,
    response_model=HealthResponse,
    summary="检查 AI 服务存活状态",
    description="确认 FastAPI 进程已经启动，并返回当前配置的模型提供方。",
)
async def liveness(
    settings: Annotated[Settings, Depends(get_settings)],
) -> HealthResponse:
    """返回进程存活状态，不调用外部模型服务。"""
    return HealthResponse(status=HealthStatus.UP, provider=settings.llm_provider)


@router.get(
    READINESS_PATH,
    response_model=HealthResponse,
    summary="检查 AI 服务就绪状态",
    description="校验当前模型 Provider 配置是否足以创建轮次解析服务。",
)
async def readiness(
    settings: Annotated[Settings, Depends(get_settings)],
) -> HealthResponse:
    """校验 Provider 配置并返回服务就绪状态，不发起真实模型请求。"""
    try:
        create_turn_resolver_service(settings)
    except ValueError as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception
    return HealthResponse(status=HealthStatus.UP, provider=settings.llm_provider)


@router.post(
    TURN_RESOLUTION_PATH,
    response_model=TurnResolutionResponse,
    dependencies=[Depends(verify_internal_token)],
    summary="解析当前对话轮次",
    description="根据 Java 提供的裁剪上下文判断消息关系和意图，并返回经过 Pydantic 校验的结果。",
)
async def resolve_turn(
    request: TurnResolutionRequest,
    service: Annotated[TurnResolverService, Depends(get_turn_resolver_service)],
) -> TurnResolutionResponse:
    """调用轮次解析 Service；日志只记录协议标识、枚举和统计信息。"""
    logger.info(
        "轮次解析请求进入, requestId=%s, schemaVersion=%s, openFlowCount=%s",
        request.request_id,
        request.schema_version,
        len(request.context.open_flow_ids),
    )
    response = await service.resolve(request)
    logger.info(
        "轮次解析处理完成, requestId=%s, relation=%s, confidence=%s",
        request.request_id,
        response.relation,
        response.confidence,
    )
    return response
