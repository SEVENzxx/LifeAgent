from fastapi.testclient import TestClient

from lifeagent_ai.config import (
    API_PREFIX,
    DEFAULT_INTERNAL_SERVICE_TOKEN,
    INTERNAL_TOKEN_HEADER,
    LIVENESS_PATH,
    TURN_RESOLUTION_PATH,
)
from lifeagent_ai.main import app


client = TestClient(app)


def test_should_report_liveness_with_active_provider() -> None:
    response = client.get(f"{API_PREFIX}{LIVENESS_PATH}")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "provider": "mock"}


def test_should_return_protocol_compatible_response_for_mock_provider() -> None:
    response = client.post(
        f"{API_PREFIX}{TURN_RESOLUTION_PATH}",
        headers={INTERNAL_TOKEN_HEADER: DEFAULT_INTERNAL_SERVICE_TOKEN},
        json={
            "request_id": "test-1",
            "schema_version": "1",
            "context": {"current_message": "你好", "open_flow_ids": []},
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "request_id": "test-1",
        "schema_version": "1",
        "relation": "SMALL_TALK",
        "target_flow_id": None,
        "intent": "SMALL_TALK",
        "confidence": 1.0,
        "reply_draft": "你好，我已经准备好了。",
    }


def test_should_publish_chinese_openapi_descriptions_for_routes_and_schemas() -> None:
    openapi = client.get("/openapi.json").json()
    operation = openapi["paths"][f"{API_PREFIX}{TURN_RESOLUTION_PATH}"]["post"]
    request_schema = openapi["components"]["schemas"]["TurnResolutionRequest"]

    assert operation["summary"] == "解析当前对话轮次"
    assert operation["description"] == (
        "根据 Java 提供的裁剪上下文判断消息关系和意图，并返回经过 Pydantic 校验的结果。"
    )
    body_schema = operation["requestBody"]["content"]["application/json"]["schema"]
    assert body_schema["$ref"] == "#/components/schemas/TurnResolutionRequest"
    assert request_schema["properties"]["request_id"]["description"] == "本次模型调用的稳定请求 ID"
