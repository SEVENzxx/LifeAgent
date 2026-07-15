import json
import logging
from time import perf_counter

import httpx
from pydantic import ValidationError

from lifeagent_ai.config import ENV_OPENAI_COMPAT_API_KEY, Settings
from lifeagent_ai.providers.base import ModelInvokeError, ModelProvider
from lifeagent_ai.prompts.turn_resolver import build_messages
from lifeagent_ai.schemas import (
    ChatCompletionRequest,
    ChatCompletionResponse,
    TurnResolutionRequest,
    TurnResolutionResponse,
)


logger = logging.getLogger(__name__)


class OpenAICompatProvider(ModelProvider):
    """调用 OpenAI 兼容接口（阿里百炼等），并在返回业务层前完成协议校验。"""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if not settings.openai_compat_api_key:
            raise ValueError(f"使用 OpenAI 兼容 Provider 时必须配置 {ENV_OPENAI_COMPAT_API_KEY}")
        self._settings = settings
        self._transport = transport

    @property
    def provider_name(self) -> str:
        return "openai-compat"

    @property
    def model_name(self) -> str:
        return self._settings.openai_compat_model

    async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
        """调用模型解析轮次；网络或协议校验失败时抛出 ModelInvokeError。"""
        payload = ChatCompletionRequest(
            model=self.model_name,
            messages=build_messages(request),
        )
        headers = {"Authorization": f"Bearer {self._settings.openai_compat_api_key}"}
        started_at = perf_counter()
        try:
            async with httpx.AsyncClient(
                base_url=self._settings.openai_compat_base_url,
                headers=headers,
                timeout=self._settings.request_timeout_seconds,
                transport=self._transport,
            ) as client:
                response = await client.post(
                    "chat/completions",
                    json=payload.model_dump(mode="json"),
                )
                response.raise_for_status()
            provider_response = ChatCompletionResponse.model_validate(response.json())
            parsed_result: object = json.loads(provider_response.choices[0].message.content)
            resolution = TurnResolutionResponse.model_validate(parsed_result)

            if resolution.request_id != request.request_id:
                raise ValueError("模型返回的 request_id 与请求不一致")
            if resolution.schema_version != request.schema_version:
                raise ValueError("模型返回的 schema_version 与请求不一致")

            duration_ms = int((perf_counter() - started_at) * 1000)
            logger.info(
                "模型调用成功, provider=%s, model=%s, durationMs=%s, resolutionStatus=%s, intent=%s, requestId=%s",
                self.provider_name, self.model_name,
                duration_ms, resolution.resolution_status,
                resolution.intent, request.request_id,
            )
            return resolution
        except (httpx.HTTPError, TypeError, ValueError, ValidationError) as exception:
            duration_ms = int((perf_counter() - started_at) * 1000)
            logger.exception(
                "模型调用失败, provider=%s, model=%s, durationMs=%s, requestId=%s",
                self.provider_name, self.model_name,
                duration_ms, request.request_id,
            )
            raise ModelInvokeError("模型服务暂时不可用") from exception
