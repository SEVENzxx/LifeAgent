package com.lifeagent.wecom;

import com.lifeagent.config.WeComProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 企业微信主动消息发送客户端。
 *
 * <p>内部维护简单同步 access_token 缓存，不依赖独立 Token Manager。
 * 只做一次发送，明确成功返回 true，其余情况返回 false 不重试。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lifeagent.wecom.enabled", havingValue = "true")
public class WeComApiClient {

    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final WeComProperties properties;
    private final RestTemplate restTemplate;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;
    private final Object tokenLock = new Object();

    @Autowired
    public WeComApiClient(WeComProperties properties) {
        this.properties = properties;
        var httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 包级可见：测试时注入预配置的 RestTemplate */
    WeComApiClient(WeComProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 发送文本消息到指定用户。
     *
     * @param toUser  企业微信用户 UserID
     * @param content 消息内容
     * @return true 表示企业微信明确返回成功，false 表示未获得明确成功结果
     */
    public boolean sendTextMessage(String toUser, String content) {
        String token = getAccessToken();
        if (token == null) {
            log.warn("企业微信 access_token 不可用，无法发送");
            return false;
        }

        SendRequest request = new SendRequest();
        request.setTouser(toUser);
        request.setAgentid(Integer.parseInt(properties.getAgentId()));
        TextMessageBody textBody = new TextMessageBody();
        textBody.setContent(content);
        request.setText(textBody);

        String url = properties.getApiBaseUrl() + "/cgi-bin/message/send?access_token=" + token;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null) {
                Object errcode = response.get("errcode");
                if (errcode instanceof Integer ec && ec == 0) {
                    return true;
                }
                log.warn("企业微信消息发送失败, errcode={}, errmsg={}, 如需添加 IP 请从 errmsg 中获取",
                        errcode, response.get("errmsg"));
            } else {
                log.warn("企业微信消息发送返回 null");
            }
            return false;
        } catch (Exception e) {
            log.warn("企业微信消息发送异常: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String getAccessToken() {
        if (cachedToken != null && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        synchronized (tokenLock) {
            if (cachedToken != null && tokenExpiresAt != null
                    && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            String url = properties.getApiBaseUrl() + "/cgi-bin/gettoken?corpid="
                    + properties.getCorpId() + "&corpsecret=" + properties.getCorpSecret();
            try {
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null) {
                    Object errcode = response.get("errcode");
                    if (Integer.valueOf(0).equals(errcode)
                            && response.get("access_token") instanceof String token
                            && response.get("expires_in") instanceof Integer expiresIn) {
                        cachedToken = token;
                        tokenExpiresAt = Instant.now()
                                .plus(Duration.ofSeconds(expiresIn))
                                .minus(TOKEN_REFRESH_MARGIN);
                        return token;
                    }
                    log.warn("获取企业微信 access_token 失败, errcode={}, errmsg={}, 如需添加 IP 请从 errmsg 中获取",
                            errcode, response.get("errmsg"));
                } else {
                    log.warn("获取企业微信 access_token 返回 null");
                }
                return null;
            } catch (Exception e) {
                log.warn("获取企业微信 access_token 异常: {}", e.getMessage());
                return null;
            }
        }
    }

    @Data
    private static class SendRequest {
        private String touser;
        private String msgtype = "text";
        private int agentid;
        private TextMessageBody text;
    }

    @Data
    private static class TextMessageBody {
        private String content;
    }
}
