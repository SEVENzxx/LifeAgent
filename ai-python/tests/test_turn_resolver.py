import asyncio
import json

import httpx
import pytest
from pydantic import ValidationError

from lifeagent_ai.config import MODEL_INVOKE_ERROR_MESSAGE, PROVIDER_FALLBACK_REPLY, Settings
from lifeagent_ai.providers import DeepSeekProvider, ModelInvokeError, ModelProvider
from lifeagent_ai.schemas import (
    ContextPackage,
    ProviderType,
    RelationType,
    TurnResolutionRequest,
    TurnResolutionResponse,
)
from lifeagent_ai.services import TurnResolverService


def create_request() -> TurnResolutionRequest:
    return TurnResolutionRequest(
        request_id="test-request",
        schema_version="1",
        context=ContextPackage(current_message="帮我安排一下", open_flow_ids=[]),
    )


def create_deepseek_settings() -> Settings:
    return Settings(
        llm_provider=ProviderType.DEEPSEEK,
        deepseek_api_key="test-key",
        deepseek_base_url="https://example.test/v1",
        deepseek_model="deepseek-chat",
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
            relation=RelationType.NEW_INTENT,
            target_flow_id=None,
            intent="CREATE_REMINDER",
            confidence=0.59,
            reply_draft="准备创建提醒。",
        )


def test_should_return_unknown_when_confidence_is_below_threshold() -> None:
    service = TurnResolverService(LowConfidenceProvider())

    response = asyncio.run(service.resolve(create_request()))

    assert response.relation is RelationType.UNKNOWN
    assert response.intent == "UNKNOWN"
    assert response.confidence == 0.0


@pytest.mark.parametrize(
    ("relation", "target_flow_id"),
    [
        (RelationType.ANSWER_FLOW, None),
        (RelationType.UNKNOWN, "flow-1"),
    ],
)
def test_should_reject_target_flow_that_conflicts_with_relation(
    relation: RelationType,
    target_flow_id: str | None,
) -> None:
    with pytest.raises(ValidationError):
        TurnResolutionResponse(
            request_id="test-request",
            schema_version="1",
            relation=relation,
            target_flow_id=target_flow_id,
            intent="UNKNOWN",
            confidence=0.9,
            reply_draft="请补充说明。",
        )


def test_should_fallback_to_unknown_when_provider_times_out() -> None:
    def timeout_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("模型调用超时", request=request)

    provider = DeepSeekProvider(
        create_deepseek_settings(),
        transport=httpx.MockTransport(timeout_handler),
    )
    service = TurnResolverService(provider)

    response = asyncio.run(service.resolve(create_request()))

    assert response.relation is RelationType.UNKNOWN
    assert response.intent == "UNKNOWN"
    assert response.confidence == 0.0
    assert response.reply_draft == PROVIDER_FALLBACK_REPLY


def test_should_reject_invalid_llm_output_before_service_receives_it() -> None:
    invalid_resolution = {
        "request_id": "test-request",
        "schema_version": "1",
        "relation": "NEW_INTENT",
        "target_flow_id": None,
        "intent": "CREATE_REMINDER",
        "confidence": 1.5,
        "reply_draft": "准备创建提醒。",
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

    provider = DeepSeekProvider(
        create_deepseek_settings(),
        transport=httpx.MockTransport(invalid_output_handler),
    )

    with pytest.raises(ModelInvokeError, match=MODEL_INVOKE_ERROR_MESSAGE):
        asyncio.run(provider.resolve_turn(create_request()))


def test_should_reject_target_flow_outside_request_context() -> None:
    invalid_resolution = {
        "request_id": "test-request",
        "schema_version": "1",
        "relation": "ANSWER_FLOW",
        "target_flow_id": "unknown-flow",
        "intent": "PROVIDE_TIME",
        "confidence": 0.9,
        "reply_draft": "已收到时间信息。",
    }

    def invalid_target_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            status_code=200,
            json={"choices": [{"message": {"content": json.dumps(invalid_resolution)}}]},
            request=request,
        )

    provider = DeepSeekProvider(
        create_deepseek_settings(),
        transport=httpx.MockTransport(invalid_target_handler),
    )

    with pytest.raises(ModelInvokeError, match=MODEL_INVOKE_ERROR_MESSAGE):
        asyncio.run(provider.resolve_turn(create_request()))
