package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.InvitationPageRequest;
import com.dayz.sc.course.model.dto.InviteAssistantRequest;
import com.dayz.sc.course.model.vo.CourseInvitationVO;
import com.dayz.sc.course.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST 控制器
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @RateLimited
    public ApiResponse<@NonNull UUID> invite(
            @Valid @RequestBody InviteAssistantRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        UUID invitationId = invitationService.invite(request, userId, role);
        return ApiResponse.ok(invitationId);
    }

    @GetMapping("/received")
    public ApiResponse<@NonNull PageResponse<@NonNull CourseInvitationVO>> listReceived(
            @Valid InvitationPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(invitationService.listReceived(request, userId));
    }

    @GetMapping("/sent")
    public ApiResponse<@NonNull PageResponse<@NonNull CourseInvitationVO>> listSent(
            @Valid InvitationPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(invitationService.listSent(request, userId));
    }

    @PutMapping("/{id}/accept")
    @RateLimited
    public ApiResponse<@NonNull Void> accept(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        invitationService.accept(id, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/decline")
    @RateLimited
    public ApiResponse<@NonNull Void> decline(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        invitationService.decline(id, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> withdraw(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        invitationService.withdraw(id, userId);
        return ApiResponse.ok(null);
    }
}
