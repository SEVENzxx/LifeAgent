package com.lifeagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 企业微信回调公共查询参数。
 *
 * <p>GET 验证 URL 和 POST 接收消息共用 {@code msg_signature}、{@code timestamp}、{@code nonce} 三个参数。
 * 使用此 DTO 统一接收避免 controller 方法签名参数过多。
 * 字段名与 query 参数名一致以支持 Spring 直接绑定。</p>
 */
@Data
@AllArgsConstructor
@Schema(description = "企业微信回调公共查询参数")
public class WeComCallbackParams {

    @Schema(description = "签名")
    private String msg_signature;

    @Schema(description = "时间戳")
    private String timestamp;

    @Schema(description = "随机数")
    private String nonce;

    /** service 层使用此方法，符合 Java camelCase 命名习惯 */
    public String msgSignature() {
        return msg_signature;
    }

    public String timestamp() {
        return timestamp;
    }

    public String nonce() {
        return nonce;
    }
}
