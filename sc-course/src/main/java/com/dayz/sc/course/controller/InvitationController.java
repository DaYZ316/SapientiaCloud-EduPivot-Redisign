package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.InvitationPageRequest;
import com.dayz.sc.course.model.dto.InviteAssistantRequest;
import com.dayz.sc.course.model.vo.CourseInvitationVO;
import com.dayz.sc.course.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<UUID> invite(
            @Valid @RequestBody InviteAssistantRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        UUID invitationId = invitationService.invite(request, userId, role);
        return ApiResponse.ok(invitationId);
    }

    @GetMapping("/received")
    public ApiResponse<PageResponse<CourseInvitationVO>> listReceived(
            @Valid InvitationPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(invitationService.listReceived(request, userId));
    }

    @GetMapping("/sent")
    public ApiResponse<PageResponse<CourseInvitationVO>> listSent(
            @Valid InvitationPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(invitationService.listSent(request, userId));
    }

    @PutMapping("/{id}/accept")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> accept(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        invitationService.accept(id, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/decline")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> decline(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        invitationService.decline(id, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> withdraw(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        invitationService.withdraw(id, userId);
        return ApiResponse.ok(null);
    }
}
