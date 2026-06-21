package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.service.ConversationService;
import com.dayz.sc.ai.service.RagChatService;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * AI 问答控制器（流式 SSE）
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;
    private final ConversationService conversationService;

    /**
     * 基于知识库的流式问答返回 text/event-stream，逐片输出答案
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimited(maxRequests = 20)
    public ResponseEntity<Flux<@NonNull ServerSentEvent<String>>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        // 校验会话归属，防止越权写入他人会话
        if (request.conversationId() != null) {
            conversationService.requireOwnedConversation(request.conversationId(), userId);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(ragChatService.stream(request, userId, role, authorization));
    }
}
