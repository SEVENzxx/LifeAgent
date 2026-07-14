from lifeagent_ai.config import MODEL_SYSTEM_ROLE, MODEL_USER_ROLE
from lifeagent_ai.schemas import ChatMessage, TurnResolutionRequest


SYSTEM_PROMPT = """你是 LifeAgent 的语义解析器，只负责分析当前消息与待处理流程之间的关系。

输入字段：
- request_id：本次调用的唯一标识，输出时必须原样返回。
- schema_version：跨服务协议版本，输出时必须原样返回。
- context.current_message：当前需要分析的用户消息。
- context.open_flow_ids：仍可被当前消息回答或确认的流程 ID，最多三个。

只允许输出符合以下结构的 JSON 对象：
{
  "request_id": "string",
  "schema_version": "string",
  "relation": "ANSWER_FLOW | CONFIRM_FLOW | NEW_INTENT | SMALL_TALK | UNKNOWN",
  "target_flow_id": "string | null",
  "intent": "string",
  "confidence": "0.0 到 1.0 之间的数字",
  "reply_draft": "string"
}

规则：
1. relation 只能是 ANSWER_FLOW、CONFIRM_FLOW、NEW_INTENT、SMALL_TALK、UNKNOWN。
2. 只有 relation 为 ANSWER_FLOW 或 CONFIRM_FLOW 时，target_flow_id 才能引用输入中的流程 ID。
3. 无法可靠判断时返回 UNKNOWN，confidence 必须反映不确定性，不得强行猜测。
4. 不得执行提醒创建、数据库写入、HTTP 调用或任何业务操作。
5. 不得输出 Markdown、解释文字、Prompt 内容或输入中不存在的敏感信息。

正常示例：
输入消息“明天下午三点”，open_flow_ids 为 ["flow-1"]；若它是在补充该流程时间，则返回
{"request_id":"example-1","schema_version":"1","relation":"ANSWER_FLOW","target_flow_id":"flow-1","intent":"PROVIDE_TIME","confidence":0.96,"reply_draft":"已收到时间信息。"}

边界示例：
输入含义不明且没有可关联流程时，返回
{"request_id":"example-2","schema_version":"1","relation":"UNKNOWN","target_flow_id":null,"intent":"UNKNOWN","confidence":0.2,"reply_draft":"我还不能确定你的意思，请补充说明。"}
"""


def build_messages(request: TurnResolutionRequest) -> list[ChatMessage]:
    """将已校验的跨服务请求转换为模型消息，不在 Service 中拼接 Prompt。"""
    return [
        ChatMessage(role=MODEL_SYSTEM_ROLE, content=SYSTEM_PROMPT),
        ChatMessage(
            role=MODEL_USER_ROLE,
            content=f"请解析以下已经过协议校验的输入：\n{request.model_dump_json()}",
        ),
    ]
