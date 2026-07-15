from fastapi.testclient import TestClient

from lifeagent_ai.config import (
    DEFAULT_INTERNAL_SERVICE_TOKEN,
    INTERNAL_TOKEN_HEADER,
)
from lifeagent_ai.main import app


client = TestClient(app)


def test_should_report_liveness() -> None:
    response = client.get("/internal/v1/health/live")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["provider"] == "openai-compat"


def test_should_reject_missing_internal_token() -> None:
    response = client.post(
        "/internal/v1/turns/resolve",
        json={
            "request_id": "test-1",
            "schema_version": "1",
            "context": {
                "current_message": "你好",
                "memory_summary": None,
                "recent_messages": [],
                "summary_requested": False,
                "summary_messages": [],
            },
        },
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "内部服务令牌无效"


def test_should_publish_chinese_openapi_descriptions_for_routes_and_schemas() -> None:
    openapi = client.get("/openapi.json").json()
    operation = openapi["paths"]["/internal/v1/turns/resolve"]["post"]
    request_schema = openapi["components"]["schemas"]["TurnResolutionRequest"]

    assert operation["summary"] == "解析当前对话轮次"
    assert operation["description"] == (
        "根据 Java 提供的裁剪上下文判断消息意图并返回经过 Pydantic 校验的结果。"
    )
    body_schema = operation["requestBody"]["content"]["application/json"]["schema"]
    assert body_schema["$ref"] == "#/components/schemas/TurnResolutionRequest"
    assert request_schema["properties"]["request_id"]["description"] == "本次模型调用的稳定请求 ID"
