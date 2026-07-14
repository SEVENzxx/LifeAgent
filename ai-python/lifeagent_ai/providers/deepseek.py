import json
import logging
from time import perf_counter

import httpx
from pydantic import ValidationError

from lifeagent_ai.config import (
    AUTHORIZATION_HEADER,
    BEARER_PREFIX,
    DEEPSEEK_CHAT_COMPLETIONS_PATH,
    DEEPSEEK_API_KEY_REQUIRED_MESSAGE,
    ENV_DEEPSEEK_API_KEY,
    MILLISECONDS_PER_SECOND,
    MODEL_INVOKE_ERROR_MESSAGE,
    MODEL_REQUEST_ID_MISMATCH_MESSAGE,
    MODEL_SCHEMA_VERSION_MISMATCH_MESSAGE,
    MODEL_TARGET_FLOW_NOT_OPEN_MESSAGE,
    Settings,
)
from lifeagent_ai.common.exceptions import ModelInvokeError
from lifeagent_ai.prompts import build_messages
from lifeagent_ai.providers.base import ModelProvider
from lifeagent_ai.schemas import (
    DeepSeekChatRequest,
    DeepSeekChatResponse,
    ProviderType,
    TurnResolutionRequest,
    TurnResolutionResponse,
)


logger = logging.getLogger(__name__)


class DeepSeekProvider(ModelProvider):
    """调用 DeepSeek，并在返回业务层前完成协议校验。"""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if not settings.deepseek_api_key:
            raise ValueError(DEEPSEEK_API_KEY_REQUIRED_MESSAGE.format(ENV_DEEPSEEK_API_KEY))
        self._settings = settings
        self._transport = transport

    @property
    def provider_name(self) -> str:
        return ProviderType.DEEPSEEK.value

    @property
    def model_name(self) -> str:
        return self._settings.deepseek_model

    async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
        """调用 DeepSeek 解析轮次；网络或协议校验失败时抛出 ModelInvokeError。"""
        payload = DeepSeekChatRequest(
            model=self.model_name,
            messages=build_messages(request),
        )
        headers = {
            AUTHORIZATION_HEADER: f"{BEARER_PREFIX} {self._settings.deepseek_api_key}"
        }
        started_at = perf_counter()
        try:
            async with httpx.AsyncClient(
                base_url=self._settings.deepseek_base_url,
                headers=headers,
                timeout=self._settings.request_timeout_seconds,
                transport=self._transport,
            ) as client:
                response = await client.post(
                    DEEPSEEK_CHAT_COMPLETIONS_PATH,
                    json=payload.model_dump(mode="json"),
                )
                response.raise_for_status()
            provider_response = DeepSeekChatResponse.model_validate(response.json())
            parsed_result: object = json.loads(provider_response.choices[0].message.content)
            resolution = TurnResolutionResponse.model_validate(parsed_result)
            self._validate_resolution(request, resolution)

            duration_ms = int((perf_counter() - started_at) * MILLISECONDS_PER_SECOND)
            logger.info(
                "模型调用成功, provider=%s, model=%s, durationMs=%s, resultType=%s, requestId=%s",
                self.provider_name,
                self.model_name,
                duration_ms,
                resolution.relation,
                request.request_id,
            )
            return resolution
        except (httpx.HTTPError, TypeError, ValueError, ValidationError) as exception:
            duration_ms = int((perf_counter() - started_at) * MILLISECONDS_PER_SECOND)
            # 这里只记录协议标识和耗时，不记录 Token、Prompt、用户正文或模型原始响应。
            logger.exception(
                "模型调用失败, provider=%s, model=%s, durationMs=%s, requestId=%s",
                self.provider_name,
                self.model_name,
                duration_ms,
                request.request_id,
            )
            raise ModelInvokeError(MODEL_INVOKE_ERROR_MESSAGE) from exception

    @staticmethod
    def _validate_resolution(
        request: TurnResolutionRequest,
        resolution: TurnResolutionResponse,
    ) -> None:
        """校验依赖原始请求的跨服务约束，失败时阻止模型结果进入业务层。"""
        # 稳定请求标识和协议版本必须原样返回，否则会破坏幂等及版本兼容判断。
        if resolution.request_id != request.request_id:
            raise ValueError(MODEL_REQUEST_ID_MISMATCH_MESSAGE)
        if resolution.schema_version != request.schema_version:
            raise ValueError(MODEL_SCHEMA_VERSION_MISMATCH_MESSAGE)
        if (
            resolution.target_flow_id is not None
            and resolution.target_flow_id not in request.context.open_flow_ids
        ):
            raise ValueError(MODEL_TARGET_FLOW_NOT_OPEN_MESSAGE)
