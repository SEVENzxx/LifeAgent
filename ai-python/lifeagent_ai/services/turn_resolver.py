import logging
from time import perf_counter

from lifeagent_ai.config import (
    MILLISECONDS_PER_SECOND,
    MIN_CONFIDENCE,
    PROVIDER_FALLBACK_REPLY,
    Settings,
    UNKNOWN_INTENT,
    UNKNOWN_REPLY,
    UNSUPPORTED_PROVIDER_MESSAGE,
)
from lifeagent_ai.common.exceptions import ModelInvokeError
from lifeagent_ai.providers import DeepSeekProvider, MockProvider, ModelProvider
from lifeagent_ai.schemas import ProviderType, RelationType, TurnResolutionRequest, TurnResolutionResponse


logger = logging.getLogger(__name__)


class TurnResolverService:
    """协调模型调用，并统一执行低置信度与调用失败降级。"""

    def __init__(self, provider: ModelProvider) -> None:
        self._provider = provider

    async def resolve(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
        """调用模型解析轮次；失败或置信度不足时返回 UNKNOWN，不修改数据库。"""
        started_at = perf_counter()
        try:
            resolution = await self._provider.resolve_turn(request)
        except ModelInvokeError:
            duration_ms = int((perf_counter() - started_at) * MILLISECONDS_PER_SECOND)
            # Provider 已记录唯一完整异常堆栈，Service 只记录降级决策，避免重复打印同一异常。
            logger.warning(
                "模型调用降级, provider=%s, model=%s, durationMs=%s, requestId=%s, fallback=UNKNOWN",
                self._provider.provider_name,
                self._provider.model_name,
                duration_ms,
                request.request_id,
            )
            return self._build_unknown_resolution(request, PROVIDER_FALLBACK_REPLY)

        if resolution.confidence < MIN_CONFIDENCE:
            duration_ms = int((perf_counter() - started_at) * MILLISECONDS_PER_SECOND)
            # 低于阈值时不强行猜测用户意图，交由 Java 侧进入澄清流程。
            logger.info(
                "低置信度结果降级, provider=%s, model=%s, durationMs=%s, requestId=%s, confidence=%s, threshold=%s",
                self._provider.provider_name,
                self._provider.model_name,
                duration_ms,
                request.request_id,
                resolution.confidence,
                MIN_CONFIDENCE,
            )
            return self._build_unknown_resolution(request, UNKNOWN_REPLY)

        duration_ms = int((perf_counter() - started_at) * MILLISECONDS_PER_SECOND)
        logger.info(
            "轮次解析成功, provider=%s, model=%s, durationMs=%s, requestId=%s, resultType=%s",
            self._provider.provider_name,
            self._provider.model_name,
            duration_ms,
            request.request_id,
            resolution.relation,
        )
        return resolution

    def _build_unknown_resolution(
        self,
        request: TurnResolutionRequest,
        reply_draft: str,
    ) -> TurnResolutionResponse:
        return TurnResolutionResponse(
            request_id=request.request_id,
            schema_version=request.schema_version,
            relation=RelationType.UNKNOWN,
            target_flow_id=None,
            intent=UNKNOWN_INTENT,
            confidence=0.0,
            reply_draft=reply_draft,
        )


def create_turn_resolver_service(settings: Settings) -> TurnResolverService:
    """按配置创建轮次解析 Service，并隔离具体 Provider 选择。"""
    if settings.llm_provider is ProviderType.MOCK:
        return TurnResolverService(MockProvider())
    if settings.llm_provider is ProviderType.DEEPSEEK:
        return TurnResolverService(DeepSeekProvider(settings))
    raise ValueError(UNSUPPORTED_PROVIDER_MESSAGE.format(settings.llm_provider))
