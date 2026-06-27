package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.model.dto.IngestDocumentRequest;
import com.dayz.sc.ai.model.vo.KnowledgeDocVO;
import com.dayz.sc.ai.service.KnowledgeBaseService;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 知识库管理控制器
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@RestController
@RequestMapping("/api/ai/knowledge-docs")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @RateLimited
    public ApiResponse<@NonNull UUID> ingest(
            @Valid @RequestBody IngestDocumentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(knowledgeBaseService.ingest(request.storageObjectId(), userId));
    }

    @GetMapping
    public ApiResponse<@NonNull List<@NonNull KnowledgeDocVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(knowledgeBaseService.list(userId, page, size));
    }

    @DeleteMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        knowledgeBaseService.delete(id, userId);
        return ApiResponse.ok();
    }
}
