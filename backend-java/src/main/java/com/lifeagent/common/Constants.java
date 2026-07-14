package com.lifeagent.common;

/**
 * 全局共享常量。
 *
 * <p>跨层复用的业务阈值、路径、Header 和运行标识统一在此维护，避免各层出现不一致的魔法值。</p>
 */
public final class Constants {

    public static final String APPLICATION_NAME = "lifeagent-java";
    public static final String API_PROFILE = "api";
    public static final String WORKER_PROFILE = "worker";
    public static final String ALL_IN_ONE_PROFILE = "all-in-one";
    public static final String DEFAULT_PROFILE = "default";
    public static final String PROFILE_SEPARATOR = ",";
    public static final String SYSTEM_API_PATH = "/api/v1/system";
    public static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    public static final String WORKER_HEARTBEAT_DELAY_PROPERTY = "${lifeagent.worker.heartbeat-interval}";

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";
    public static final String UUID_SEPARATOR = "-";
    public static final String EMPTY_STRING = "";
    public static final String SUCCESS_MESSAGE = "ok";
    public static final String INVALID_REQUEST_MESSAGE = "请求参数不合法";
    public static final String SERVICE_UNAVAILABLE_MESSAGE = "服务暂时不可用";

    public static final int MAX_MESSAGE_LENGTH = 4_000;
    public static final int MAX_OPEN_FLOW_COUNT = 3;
    public static final int MAX_IDENTIFIER_LENGTH = 100;
    public static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    public static final String SCHEMA_VERSION_PATTERN = "^\\d+$";
    public static final String MIN_CONFIDENCE_VALUE = "0.0";
    public static final String MAX_CONFIDENCE_VALUE = "1.0";
    public static final String CURRENT_MESSAGE_TOO_LONG_MESSAGE =
            "当前消息不能超过 " + MAX_MESSAGE_LENGTH + " 个字符";
    public static final String CURRENT_MESSAGE_REQUIRED_MESSAGE = "当前消息不能为空";
    public static final String OPEN_FLOW_LIST_REQUIRED_MESSAGE = "打开流程列表不能为空";
    public static final String OPEN_FLOW_COUNT_EXCEEDED_MESSAGE = "打开流程不能超过三个";
    public static final String FLOW_ID_REQUIRED_MESSAGE = "流程 ID 不能为空";
    public static final String REQUEST_ID_REQUIRED_MESSAGE = "请求 ID 不能为空";
    public static final String SCHEMA_VERSION_REQUIRED_MESSAGE = "协议版本不能为空";
    public static final String SCHEMA_VERSION_INVALID_MESSAGE = "协议版本只能包含数字";
    public static final String CONTEXT_REQUIRED_MESSAGE = "上下文不能为空";
    public static final String RELATION_TYPE_REQUIRED_MESSAGE = "关系类型不能为空";
    public static final String INTENT_REQUIRED_MESSAGE = "意图不能为空";
    public static final String CONFIDENCE_REQUIRED_MESSAGE = "置信度不能为空";
    public static final String CONFIDENCE_TOO_LOW_MESSAGE = "置信度不能小于 0.0";
    public static final String CONFIDENCE_TOO_HIGH_MESSAGE = "置信度不能大于 1.0";
    public static final String REPLY_REQUIRED_MESSAGE = "建议回复不能为空";
    public static final String TARGET_FLOW_RELATION_INVALID_MESSAGE = "目标流程与关系类型不匹配";
    public static final String IDENTIFIER_TOO_LONG_MESSAGE =
            "标识不能超过 " + MAX_IDENTIFIER_LENGTH + " 个字符";
    public static final String REPLY_TOO_LONG_MESSAGE =
            "建议回复不能超过 " + MAX_MESSAGE_LENGTH + " 个字符";

    private Constants() {
    }
}
