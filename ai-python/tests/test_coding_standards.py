from pydantic import BaseModel

from lifeagent_ai.schemas import (
    ChatCompletionChoice,
    ChatCompletionMessage,
    ChatCompletionRequest,
    ChatCompletionResponse,
    ChatMessage,
    ContextPackage,
    HealthResponse,
    TurnResolutionRequest,
    TurnResolutionResponse,
)


SCHEMA_TYPES: tuple[type[BaseModel], ...] = (
    ChatCompletionChoice,
    ChatCompletionMessage,
    ChatCompletionRequest,
    ChatCompletionResponse,
    ChatMessage,
    ContextPackage,
    HealthResponse,
    TurnResolutionRequest,
    TurnResolutionResponse,
)


def test_should_document_every_pydantic_field_in_chinese() -> None:
    """防止新增协议字段时遗漏 OpenAPI 中文说明。"""
    for schema_type in SCHEMA_TYPES:
        for field_name, field_info in schema_type.model_fields.items():
            assert field_info.description, f"字段 {schema_type.__name__}.{field_name} 缺少中文说明"
            assert any("一" <= character <= "鿿" for character in field_info.description), (
                f"字段 {schema_type.__name__}.{field_name} 必须使用中文说明"
            )
