package com.lifeagent.common;

/**
 * 表示调用方可以理解和处理的业务异常。
 */
public class BizException extends RuntimeException {

    /**
     * 使用安全的业务提示创建异常。
     *
     * @param message 可返回给调用方的业务提示
     */
    public BizException(String message) {
        super(message);
    }
}
