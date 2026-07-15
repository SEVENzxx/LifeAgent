package com.lifeagent.wecom;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 企业微信回调处理结果。
 *
 * <p>包含 HTTP 状态码和响应体，由 controller 映射为 HTTP 响应。</p>
 */
@Data
@AllArgsConstructor
public class WeComCallbackResult {

    private int httpStatus;
    private String body;
}
