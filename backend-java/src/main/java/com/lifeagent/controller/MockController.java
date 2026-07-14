package com.lifeagent.controller;

import com.lifeagent.common.ApiResponse;
import com.lifeagent.dto.InboundMessageRequest;
import com.lifeagent.dto.InboundMessageResponse;
import com.lifeagent.dto.OutboundMessageResponse;
import com.lifeagent.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mock 消息渠道控制器。
 *
 * <p>默认关闭，通过 lifeagent.mock-channel.enabled=true 启用。
 * 同步处理：接收消息后直接持久化、生成回复并返回。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lifeagent.mock-channel.enabled", havingValue = "true")
@Tag(name = "Mock 消息渠道", description = "用于开发和测试的 Mock 消息收发接口")
@RequestMapping("/api/v1/mock/messages")
public class MockController {

    private final ConversationService conversationService;

    @Operation(summary = "接收 Mock 入站消息", description = "接收消息，完成幂等检测、持久化并返回固定回复")
    @PostMapping("/inbound")
    public ResponseEntity<InboundMessageResponse> receiveInbound(
            @Valid @RequestBody @Parameter(description = "入站消息请求") InboundMessageRequest request
    ) {
        log.debug("Mock 入站请求进入, externalMessageId={}, externalUserId={}",
                request.getExternalMessageId(), request.getExternalUserId());
        InboundMessageResponse response = conversationService.receiveInboundMessage(request);
        log.info("Mock 入站消息处理完成, messageId={}, duplicate={}",
                response.getMessageId(), response.isDuplicate());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "查询已发送回复", description = "查询指定 Mock 用户已成功发送的回复列表")
    @GetMapping("/outbound")
    public ApiResponse<List<OutboundMessageResponse>> listOutbound(
            @RequestParam @NotBlank @Parameter(description = "外部用户 ID") String externalUserId
    ) {
        log.debug("Mock 出站查询请求进入, externalUserId={}", externalUserId);
        List<OutboundMessageResponse> replies = conversationService.listSentReplies(externalUserId);
        return ApiResponse.ok(replies);
    }
}
