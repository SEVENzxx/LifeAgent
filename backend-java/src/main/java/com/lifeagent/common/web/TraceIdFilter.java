package com.lifeagent.common.web;

import com.lifeagent.common.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个 HTTP 请求建立安全的 traceId，并记录请求进入与完成节点。
 *
 * <p>外部 traceId 只有通过字符白名单校验后才会进入 MDC，避免换行符污染日志。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_TRACE_ID = Pattern.compile(Constants.TRACE_ID_PATTERN);

    /**
     * 解析或生成 traceId，在请求结束后清理 MDC，避免线程复用造成链路标识串扰。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(Constants.TRACE_ID_HEADER));
        boolean logRequest = shouldLogRequest(request.getRequestURI());
        long startedAt = System.nanoTime();

        MDC.put(Constants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(Constants.TRACE_ID_HEADER, traceId);
        try {
            if (logRequest) {
                log.info("请求进入, method={}, path={}", request.getMethod(), request.getRequestURI());
            }
            filterChain.doFilter(request, response);
        } finally {
            if (logRequest) {
                long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                log.info("请求处理完成, method={}, path={}, status={}, durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMillis);
            }
            MDC.remove(Constants.TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(String candidate) {
        if (candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean shouldLogRequest(String requestUri) {
        return !requestUri.startsWith(Constants.ACTUATOR_HEALTH_PATH);
    }
}
