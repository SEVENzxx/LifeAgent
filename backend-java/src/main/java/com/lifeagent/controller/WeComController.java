package com.lifeagent.controller;

import com.lifeagent.common.ResponseHelper;
import com.lifeagent.dto.WeComCallbackParams;
import com.lifeagent.wecom.WeComCallbackResult;
import com.lifeagent.wecom.WeComCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 企业微信回调控制器。
 *
 * <p>条件注册：仅 lifeagent.wecom.enabled=true 时注册。
 * 所有业务验证逻辑委托给 {@link WeComCallbackService}，
 * controller 只负责参数绑定与 HTTP 响应映射。</p>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@ConditionalOnProperty(name = "lifeagent.wecom.enabled", havingValue = "true")
@Tag(name = "企业微信回调", description = "企业微信自建应用消息回调接口")
@RequestMapping("/api/v1/channels/wecom/callback")
public class WeComController {

    private final WeComCallbackService callbackService;

    @Operation(summary = "验证回调 URL", description = "企业微信管理后台验证回调 URL 的有效性")
    @GetMapping
    public ResponseEntity<String> verifyUrl(
            WeComCallbackParams params,
            @RequestParam @Parameter(description = "加密的 echostr，Web 框架已解码一次，禁止二次 URL decode") String echostr
    ) {
        WeComCallbackResult result = callbackService.verifyUrl(params, echostr);
        return ResponseHelper.toResponse(result.getHttpStatus(), result.getBody());
    }

    @Operation(summary = "接收消息回调", description = "接收企业微信推送的用户文本消息，幂等持久化后返回 success")
    @PostMapping(consumes = {"application/xml", "text/xml"})
    public ResponseEntity<String> receiveMessage(
            WeComCallbackParams params,
            @RequestBody @Parameter(description = "加密 XML 消息体") String body
    ) {
        WeComCallbackResult result = callbackService.receiveMessage(params, body);
        return ResponseHelper.toResponse(result.getHttpStatus(), result.getBody());
    }
}
