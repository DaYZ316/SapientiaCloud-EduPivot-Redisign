package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.service.ConversationService;
import com.dayz.sc.ai.service.RagChatService;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * AI 问答控制器（流式 SSE）。
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
     * 基于知识库的流式问答。返回 text/event-stream，逐片输出答案。
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    public Flux<String> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        // 校验会话归属，防止越权写入他人会话
        conversationService.requireOwnedConversation(request.conversationId(), userId);
        return ragChatService.streamChat(request.conversationId(), userId, request.message());
    }
}
