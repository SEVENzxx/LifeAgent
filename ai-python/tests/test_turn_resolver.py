import asyncio
import json

import httpx
import pytest
from pydantic import ValidationError

from lifeagent_ai.config import (
    INTENT_SMALL_TALK,
    RESOLUTION_AI_UNAVAILABLE,
    RESOLUTION_RESOLVED,
    Settings,
)
from lifeagent_ai.providers.base import ModelInvokeError, ModelProvider
from lifeagent_ai.providers.openai_compat import OpenAICompatProvider
from lifeagent_ai.schemas import (
    ContextMessageItem,
    ContextPackage,
    TurnResolutionRequest,
    TurnResolutionResponse,
)
from lifeagent_ai.services.turn_resolver import resolve_turn


def create_request() -> TurnResolutionRequest:
    return TurnResolutionRequest(
        request_id="test-request",
        schema_version="1",
        context=ContextPackage(
            current_message="你好",
            memory_summary=None,
            recent_messages=[],
            summary_requested=False,
            summary_messages=[],
        ),
    )


def create_openai_compat_settings() -> Settings:
    return Settings(
        llm_provider="openai-compat",
        openai_compat_api_key="test-key",
        openai_compat_base_url="https://example.test/v1",
        openai_compat_model="qwen-turbo",
        internal_service_token="test-token",
        request_timeout_seconds=0.1,
    )


class LowConfidenceProvider(ModelProvider):

    @property
    def provider_name(self) -> str:
        return "test"

    @property
    def model_name(self) -> str:
        return "low-confidence"

    async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
        return TurnResolutionResponse(
            request_id=request.request_id,
            schema_version=request.schema_version,
            resolution_status=RESOLUTION_RESOLVED,
            intent=INTENT_SMALL_TALK,
            confidence=0.59,
            reply_draft="你好！",
            updated_summary=None,
        )


def test_should_use_small_talk_when_confidence_is_below_threshold() -> None:
    response = asyncio.run(resolve_turn(LowConfidenceProvider(), create_request()))
    # 低置信度不再降级为 NEEDS_CLARIFICATION+UNKNOWN，直接使用 LLM 结果
    assert response.resolution_status == RESOLUTION_RESOLVED
    assert response.intent == INTENT_SMALL_TALK
    assert response.confidence < 0.6


@pytest.mark.parametrize(
    ("status", "intent", "confidence", "reply_draft", "updated_summary"),
    [
        (RESOLUTION_RESOLVED, INTENT_SMALL_TALK, 0.9, None, None),
        (RESOLUTION_AI_UNAVAILABLE, INTENT_SMALL_TALK, 0.5, None, None),
        (RESOLUTION_AI_UNAVAILABLE, INTENT_SMALL_TALK, 0.0, "not null", None),
        (RESOLUTION_AI_UNAVAILABLE, INTENT_SMALL_TALK, 0.0, None, "summary"),
    ],
)
def test_should_reject_cross_field_violations(
    status: str,
    intent: str,
    confidence: float,
    reply_draft: str | None,
    updated_summary: str | None,
) -> None:
    with pytest.raises(ValidationError):
        TurnResolutionResponse(
            request_id="test",
            schema_version="1",
            resolution_status=status,
            intent=intent,
            confidence=confidence,
            reply_draft=reply_draft,
            updated_summary=updated_summary,
        )


def test_should_fallback_to_unavailable_when_provider_times_out() -> None:
    def timeout_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("模型调用超时", request=request)

    provider = OpenAICompatProvider(
        create_openai_compat_settings(),
        transport=httpx.MockTransport(timeout_handler),
    )
    response = asyncio.run(resolve_turn(provider, create_request()))
    assert response.resolution_status == RESOLUTION_AI_UNAVAILABLE
    assert response.intent == INTENT_SMALL_TALK
    assert response.confidence == 0.0
    assert response.reply_draft is None


def test_should_reject_invalid_llm_output_before_service_receives_it() -> None:
    invalid_resolution = {
        "request_id": "test-request",
        "schema_version": "1",
        "resolution_status": "RESOLVED",
        "intent": "SMALL_TALK",
        "confidence": 1.5,
        "reply_draft": "你好",
        "updated_summary": None,
    }

    def invalid_output_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            status_code=200,
            json={
                "choices": [
                    {"message": {"content": json.dumps(invalid_resolution)}}
                ]
            },
            request=request,
        )

    provider = OpenAICompatProvider(
        create_openai_compat_settings(),
        transport=httpx.MockTransport(invalid_output_handler),
    )
    with pytest.raises(ModelInvokeError, match="模型服务暂时不可用"):
        asyncio.run(provider.resolve_turn(create_request()))


def test_should_clear_updated_summary_when_not_requested() -> None:
    class SummaryProvider(ModelProvider):
        @property
        def provider_name(self) -> str:
            return "test"

        @property
        def model_name(self) -> str:
            return "summary-test"

        async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
            return TurnResolutionResponse(
                request_id=request.request_id,
                schema_version=request.schema_version,
                resolution_status=RESOLUTION_RESOLVED,
                intent=INTENT_SMALL_TALK,
                confidence=0.95,
                reply_draft="你好",
                updated_summary="不应存在的摘要",
            )

    request = create_request()
    response = asyncio.run(resolve_turn(SummaryProvider(), request))
    assert response.updated_summary is None


def test_should_keep_updated_summary_when_requested() -> None:
    class SummaryProvider(ModelProvider):
        @property
        def provider_name(self) -> str:
            return "test"

        @property
        def model_name(self) -> str:
            return "summary-test"

        async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
            return TurnResolutionResponse(
                request_id=request.request_id,
                schema_version=request.schema_version,
                resolution_status=RESOLUTION_RESOLVED,
                intent=INTENT_SMALL_TALK,
                confidence=0.95,
                reply_draft="你好",
                updated_summary="这是新的摘要",
            )

    request = TurnResolutionRequest(
        request_id="test-request",
        schema_version="1",
        context=ContextPackage(
            current_message="你好",
            memory_summary=None,
            recent_messages=[],
            summary_requested=True,
            summary_messages=[
                ContextMessageItem(message_id=1, role="USER", content="昨天我去了公园"),
            ],
        ),
    )
    response = asyncio.run(resolve_turn(SummaryProvider(), request))
    assert response.updated_summary == "这是新的摘要"


def test_should_reject_unknown_intent() -> None:
    with pytest.raises(ValidationError):
        TurnResolutionResponse(
            request_id="test",
            schema_version="1",
            resolution_status=RESOLUTION_RESOLVED,
            intent="INVALID_INTENT",
            confidence=0.9,
            reply_draft="hello",
            updated_summary=None,
        )


def test_should_reject_unknown_resolution_status() -> None:
    with pytest.raises(ValidationError):
        TurnResolutionResponse(
            request_id="test",
            schema_version="1",
            resolution_status="INVALID_STATUS",
            intent=INTENT_SMALL_TALK,
            confidence=0.0,
            reply_draft=None,
            updated_summary=None,
        )
