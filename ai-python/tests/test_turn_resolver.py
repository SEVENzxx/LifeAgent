import asyncio
import json

import httpx
import pytest
from pydantic import ValidationError

from lifeagent_ai.config import (
    ENV_OPENAI_COMPAT_TRUST_ENV,
    INTENT_SMALL_TALK,
    RESOLUTION_AI_UNAVAILABLE,
    RESOLUTION_RESOLVED,
    Settings,
    get_settings,
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
        openai_compat_trust_env=False,
        internal_service_token="test-token",
        request_timeout_seconds=0.1,
    )


@pytest.mark.parametrize(
    ("raw_value", "expected"),
    [("true", True), ("1", True), ("false", False), ("invalid", False)],
)
def test_should_parse_environment_proxy_setting(
    monkeypatch: pytest.MonkeyPatch,
    raw_value: str,
    expected: bool,
) -> None:
    monkeypatch.setenv(ENV_OPENAI_COMPAT_TRUST_ENV, raw_value)
    get_settings.cache_clear()
    try:
        assert get_settings().openai_compat_trust_env is expected
    finally:
        get_settings.cache_clear()


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


def test_should_forward_environment_proxy_setting_to_http_client(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured_options: dict[str, object] = {}
    valid_resolution = {
        "request_id": "test-request",
        "schema_version": "1",
        "resolution_status": "RESOLVED",
        "intent": "SMALL_TALK",
        "confidence": 0.9,
        "reply_draft": "你好",
        "updated_summary": None,
    }
    valid_content = json.dumps(valid_resolution)

    class RecordingAsyncClient:
        def __init__(self, **options: object) -> None:
            captured_options.update(options)

        async def __aenter__(self) -> "RecordingAsyncClient":
            return self

        async def __aexit__(self, *args: object) -> None:
            return None

        async def post(self, path: str, json: object) -> httpx.Response:
            request = httpx.Request("POST", f"https://example.test/v1/{path}")
            return httpx.Response(
                status_code=200,
                json={
                    "choices": [
                        {"message": {"content": valid_content}}
                    ]
                },
                request=request,
            )

    monkeypatch.setattr(httpx, "AsyncClient", RecordingAsyncClient)
    provider = OpenAICompatProvider(create_openai_compat_settings())

    response = asyncio.run(provider.resolve_turn(create_request()))

    assert response.resolution_status == RESOLUTION_RESOLVED
    assert captured_options["trust_env"] is False


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


# ========== LA-005 提醒字段测试 ==========


def test_should_accept_reminder_resolution_in_response() -> None:
    """REMINDER_CREATE 意图可以包含 reminder_resolution。"""
    response = TurnResolutionResponse(
        request_id="test",
        schema_version="1",
        resolution_status=RESOLUTION_RESOLVED,
        intent="REMINDER_CREATE",
        confidence=0.9,
        reply_draft="已设置提醒",
        updated_summary=None,
        reminder_resolution={
            "target": "NEW",
            "action": "CREATE",
            "content": "参加面试",
            "event_at": None,
            "remind_at": "2026-07-18T15:00:00+08:00",
            "advance_remind_at": None,
            "time_source": "USER_EXPLICIT",
            "missing_fields": [],
        },
    )
    assert response.reminder_resolution is not None
    assert response.reminder_resolution.target == "NEW"
    assert response.reminder_resolution.action == "CREATE"
    assert response.reminder_resolution.content == "参加面试"


def test_should_reject_reminder_resolution_for_non_reminder_intent() -> None:
    """非 REMINDER_CREATE 意图时不允许包含 reminder_resolution。"""
    with pytest.raises(ValidationError, match="非 REMINDER_CREATE 意图"):
        TurnResolutionResponse(
            request_id="test",
            schema_version="1",
            resolution_status=RESOLUTION_RESOLVED,
            intent=INTENT_SMALL_TALK,
            confidence=0.9,
            reply_draft="你好",
            updated_summary=None,
            reminder_resolution={
                "target": "NEW",
                "action": "CREATE",
                "content": "test",
                "time_source": "USER_EXPLICIT",
                "missing_fields": [],
            },
        )


def test_should_accept_context_package_with_reminder_fields() -> None:
    """ContextPackage 可以包含 reminder 上下文。"""
    context = ContextPackage(
        current_message="提醒我明天面试",
        memory_summary=None,
        recent_messages=[],
        summary_requested=False,
        summary_messages=[],
        reference_time="2026-07-17T12:00:00+08:00",
        timezone="Asia/Shanghai",
        pending_reminder={
            "draft_token": "draft-1",
            "content": "面试",
            "time_source": "AI_SUGGESTED",
            "draft_status": "AWAITING_CONFIRMATION",
        },
        recent_reminder=None,
    )
    assert context.reference_time == "2026-07-17T12:00:00+08:00"
    assert context.timezone == "Asia/Shanghai"
    assert context.pending_reminder is not None
    assert context.pending_reminder.draft_token == "draft-1"
    assert context.recent_reminder is None


def test_should_reject_reminder_resolution_with_unknown_target() -> None:
    """非法 target 应被拒绝。"""
    with pytest.raises(ValidationError):
        TurnResolutionResponse(
            request_id="test",
            schema_version="1",
            resolution_status=RESOLUTION_RESOLVED,
            intent="REMINDER_CREATE",
            confidence=0.9,
            reply_draft="test",
            updated_summary=None,
            reminder_resolution={
                "target": "INVALID",
                "action": "CREATE",
                "content": "test",
                "time_source": "USER_EXPLICIT",
                "missing_fields": [],
            },
        )
