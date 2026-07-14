package com.lifeagent.common;

/**
 * 跨文件复用的业务阈值和标识符。
 *
 * <p>单次使用的值直接在调用处内联，不在此维护。</p>
 */
public final class Constants {

    public static final String APPLICATION_NAME = "lifeagent-java";
    public static final String DEFAULT_PROFILE = "default";
    public static final String PROFILE_SEPARATOR = ",";
    public static final String SYSTEM_API_PATH = "/api/v1/system";
    public static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    public static final int MAX_MESSAGE_LENGTH = 4_000;
    public static final int MAX_OPEN_FLOW_COUNT = 3;
    public static final int MAX_IDENTIFIER_LENGTH = 100;

    public static final String SCHEMA_VERSION_PATTERN = "^\\d+$";
    public static final String MIN_CONFIDENCE_VALUE = "0.0";
    public static final String MAX_CONFIDENCE_VALUE = "1.0";

    private Constants() {
    }
}
