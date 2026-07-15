import logging
from time import perf_counter

from lifeagent_ai.config import (
    INTENT_SMALL_TALK,
    MIN_CONFIDENCE,
    RESOLUTION_AI_UNAVAILABLE,
)
from lifeagent_ai.providers.base import ModelInvokeError, ModelProvider
from lifeagent_ai.schemas import TurnResolutionRequest, TurnResolutionResponse


logger = logging.getLogger(__name__)


async def resolve_turn(provider: ModelProvider, request: TurnResolutionRequest) -> TurnResolutionResponse:
    """调用模型解析轮次；失败时返回 AI_UNAVAILABLE，低置信度仍使用 LLM 结果。"""
    started_at = perf_counter()
    try:
        resolution = await provider.resolve_turn(request)
    except ModelInvokeError:
        duration_ms = int((perf_counter() - started_at) * 1000)
        logger.warning(
            "模型调用降级, provider=%s, model=%s, durationMs=%s, requestId=%s, fallback=AI_UNAVAILABLE",
            provider.provider_name, provider.model_name,
            duration_ms, request.request_id,
        )
        return TurnResolutionResponse(
            request_id=request.request_id,
            schema_version=request.schema_version,
            resolution_status=RESOLUTION_AI_UNAVAILABLE,
            intent=INTENT_SMALL_TALK,
            confidence=0.0,
            reply_draft=None,
            updated_summary=None,
        )

    # 低置信度不降级，直接使用 LLM 原始结果
    if resolution.confidence < MIN_CONFIDENCE:
        duration_ms = int((perf_counter() - started_at) * 1000)
        logger.info(
            "低置信度结果, provider=%s, model=%s, durationMs=%s, requestId=%s, "
            "confidence=%s, threshold=%s",
            provider.provider_name, provider.model_name,
            duration_ms, request.request_id,
            resolution.confidence, MIN_CONFIDENCE,
        )

    # 未请求摘要时清除 updated_summary
    if not request.context.summary_requested and resolution.updated_summary is not None:
        resolution = TurnResolutionResponse(
            request_id=resolution.request_id,
            schema_version=resolution.schema_version,
            resolution_status=resolution.resolution_status,
            intent=resolution.intent,
            confidence=resolution.confidence,
            reply_draft=resolution.reply_draft,
            updated_summary=None,
        )

    duration_ms = int((perf_counter() - started_at) * 1000)
    logger.info(
        "轮次解析成功, provider=%s, model=%s, durationMs=%s, requestId=%s, "
        "resolutionStatus=%s, intent=%s, confidence=%s",
        provider.provider_name, provider.model_name,
        duration_ms, request.request_id,
        resolution.resolution_status, resolution.intent,
        resolution.confidence,
    )
    return resolution
