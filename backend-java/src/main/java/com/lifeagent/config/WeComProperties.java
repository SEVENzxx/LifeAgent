package com.lifeagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信渠道配置。
 *
 * <p>enabled=false 时不校验其余字段，Mock 和应用健康检查正常。
 * enabled=true 时所有必填字段启动即校验，缺失或格式非法必须启动失败。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lifeagent.wecom")
public class WeComProperties {

    /**
     * 是否启用企业微信渠道。
     * 默认关闭，enabled=false 时不校验其余配置。
     */
    private boolean enabled = false;

    /**
     * 企业微信 CorpID（企业 ID）
     */
    private String corpId;

    /**
     * 企业微信自建应用 AgentId
     */
    private String agentId;

    /**
     * 回调 token，用于签名校验
     */
    private String callbackToken;

    /**
     * 回调 EncodingAESKey，用于加解密
     */
    private String encodingAesKey;

    /**
     * 企业微信 CorpSecret，用于获取 access_token
     */
    private String corpSecret;

    /**
     * 企业微信 API 基础 URL，测试可覆盖。
     * 生产默认只允许 HTTPS。
     */
    private String apiBaseUrl = "https://qyapi.weixin.qq.com";
}
