from lifeagent_ai.config import (
    MOCK_DEFAULT_REPLY,
    MOCK_MODEL_NAME,
    MOCK_SMALL_TALK_REPLY,
    MOCK_SMALL_TALK_WORDS,
    SMALL_TALK_INTENT,
    UNCLASSIFIED_INTENT,
)
from lifeagent_ai.providers.base import ModelProvider
from lifeagent_ai.schemas import ProviderType, RelationType, TurnResolutionRequest, TurnResolutionResponse


class MockProvider(ModelProvider):
    """提供确定性本地结果，用于开发环境和自动化测试。"""

    @property
    def provider_name(self) -> str:
        return ProviderType.MOCK.value

    @property
    def model_name(self) -> str:
        return MOCK_MODEL_NAME

    async def resolve_turn(self, request: TurnResolutionRequest) -> TurnResolutionResponse:
        """按固定关键词解析轮次，不调用任何外部服务。"""
        message = request.context.current_message
        is_small_talk = any(word in message for word in MOCK_SMALL_TALK_WORDS)
        relation = RelationType.SMALL_TALK if is_small_talk else RelationType.NEW_INTENT
        intent = SMALL_TALK_INTENT if is_small_talk else UNCLASSIFIED_INTENT
        reply = MOCK_SMALL_TALK_REPLY if is_small_talk else MOCK_DEFAULT_REPLY
        return TurnResolutionResponse(
            request_id=request.request_id,
            schema_version=request.schema_version,
            relation=relation,
            intent=intent,
            confidence=1.0,
            reply_draft=reply,
        )
