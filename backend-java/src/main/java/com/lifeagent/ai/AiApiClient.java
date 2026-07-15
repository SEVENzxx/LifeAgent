package com.lifeagent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lifeagent.config.AiProperties;
import com.lifeagent.dto.turn.TurnResolutionRequest;
import com.lifeagent.dto.turn.TurnResolutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;

import static com.lifeagent.common.Constants.*;

/**
 * AI 服务 HTTP 客户端，负责调用 Python Turn Resolver。
 */
@Slf4j
@Component
public class AiApiClient {

    private final AiProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiApiClient(AiProperties properties) {
        this.properties = properties;
        var httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = JsonMapper.builder().build();
    }

    /**
     * 调用 Python Turn Resolver。
     *
     * @return TurnResolutionResponse；通信或校验失败时返回 AI_UNAVAILABLE 状态
     */
    public TurnResolutionResponse resolve(TurnResolutionRequest request) {
        String url = properties.getBaseUrl() + AI_TURN_RESOLVE_PATH;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = properties.getInternalToken();
        if (!token.isBlank()) {
            headers.set(INTERNAL_TOKEN_HEADER, token);
        }

        HttpEntity<String> httpRequest;
        try {
            String json = objectMapper.writeValueAsString(request);
            httpRequest = new HttpEntity<>(json, headers);
        } catch (Exception e) {
            log.error("序列化 TurnResolutionRequest 失败, requestId={}", request.getRequestId(), e);
            return buildUnavailable(request.getRequestId());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpRequest, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("AI 服务返回非成功状态, status={}, requestId={}",
                        response.getStatusCode(), request.getRequestId());
                return buildUnavailable(request.getRequestId());
            }

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("AI 服务返回空响应, requestId={}", request.getRequestId());
                return buildUnavailable(request.getRequestId());
            }

            TurnResolutionResponse parsed = objectMapper.readValue(body, TurnResolutionResponse.class);

            // 校验协议一致性
            if (!request.getRequestId().equals(parsed.getRequestId())) {
                log.warn("AI 返回 requestId 不匹配, expected={}, actual={}",
                        request.getRequestId(), parsed.getRequestId());
                return buildUnavailable(request.getRequestId());
            }
            if (!request.getSchemaVersion().equals(parsed.getSchemaVersion())) {
                log.warn("AI 返回 schemaVersion 不匹配, expected={}, actual={}",
                        request.getSchemaVersion(), parsed.getSchemaVersion());
                return buildUnavailable(request.getRequestId());
            }

            // resolutionStatus 校验
            String status = parsed.getResolutionStatus();
            if (!STATUS_RESOLVED.equals(status)
                    && !STATUS_NEEDS_CLARIFICATION.equals(status)
                    && !STATUS_AI_UNAVAILABLE.equals(status)) {
                log.warn("AI 返回未知 resolutionStatus={}, requestId={}", status, request.getRequestId());
                return buildUnavailable(request.getRequestId());
            }

            return parsed;

        } catch (ResourceAccessException e) {
            log.warn("AI 服务连接超时或不可达, requestId={}, err={}",
                    request.getRequestId(), e.getMessage());
            return buildUnavailable(request.getRequestId());
        } catch (Exception e) {
            log.warn("AI 服务调用异常, requestId={}", request.getRequestId(), e);
            return buildUnavailable(request.getRequestId());
        }
    }

    private TurnResolutionResponse buildUnavailable(String requestId) {
        return TurnResolutionResponse.builder()
                .requestId(requestId)
                .schemaVersion(SCHEMA_VERSION)
                .resolutionStatus(STATUS_AI_UNAVAILABLE)
                .intent(INTENT_SMALL_TALK)
                .confidence(0.0)
                .replyDraft(null)
                .updatedSummary(null)
                .build();
    }
}
