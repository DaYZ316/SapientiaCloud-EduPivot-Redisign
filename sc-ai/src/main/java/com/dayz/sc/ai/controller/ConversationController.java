package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.model.dto.CreateConversationRequest;
import com.dayz.sc.ai.model.dto.UpdateConversationRequest;
import com.dayz.sc.ai.model.vo.ChatMessageVO;
import com.dayz.sc.ai.model.vo.ConversationVO;
import com.dayz.sc.ai.service.ConversationService;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 会话管理控制器
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@RestController
@RequestMapping("/api/ai/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ApiResponse<@NonNull UUID> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(conversationService.createConversation(request, userId));
    }

    @GetMapping
    public ApiResponse<@NonNull List<@NonNull ConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(conversationService.listConversations(userId, page, size));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<@NonNull List<@NonNull ChatMessageVO>> listMessages(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(conversationService.listMessages(id, userId));
    }

    @PatchMapping("/{id}")
    public ApiResponse<@NonNull Void> updateConversation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        conversationService.updateConversation(id, request, userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<@NonNull Void> deleteConversation(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        conversationService.deleteConversation(id, userId);
        return ApiResponse.ok();
    }
}
