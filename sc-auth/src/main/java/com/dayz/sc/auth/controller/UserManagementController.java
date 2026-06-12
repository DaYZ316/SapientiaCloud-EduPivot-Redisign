package com.dayz.sc.auth.controller;

import com.dayz.sc.auth.model.dto.UpdateUserRequest;
import com.dayz.sc.auth.model.dto.UserBasicInfo;
import com.dayz.sc.auth.model.dto.UserPageRequest;
import com.dayz.sc.auth.model.vo.UserProfileVO;
import com.dayz.sc.auth.service.UserManagementService;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.common.security.support.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 系统用户分页查询与账号资料维护接口。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserManagementController {
    private final UserManagementService userManagementService;

    @GetMapping("/me")
    public ApiResponse<@NonNull UserProfileVO> currentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.getUser(userId));
    }

    @PutMapping("/me")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<@NonNull UserProfileVO> updateCurrentUser(@Valid @RequestBody UpdateUserRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.updateCurrentUser(userId, request));
    }

    @GetMapping
    public ApiResponse<@NonNull PageResponse<@NonNull UserProfileVO>> pageUsers(
            UserPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ApiResponse.ok(userManagementService.pageUsers(request));
    }

    @PutMapping("/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<@NonNull UserProfileVO> updateUser(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateUserRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ApiResponse.ok(userManagementService.updateUser(id, request));
    }

    @PutMapping("/{id}/reset-password")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> resetPassword(@PathVariable UUID id,
                                           @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        userManagementService.resetPassword(id);
        return ApiResponse.ok(null);
    }

    /**
     * 内部接口：批量获取用户基本信息（displayName, avatarUrl）。
     * 用于服务间通信，如课程服务获取教师信息。
     */
    @GetMapping("/internal/basic")
    public ApiResponse<@NonNull List<@NonNull UserBasicInfo>> getUsersBasicInfo(@RequestParam List<UUID> ids) {
        return ApiResponse.ok(userManagementService.getUsersBasicInfo(ids));
    }

    private void requireAdmin(Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (!SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }
}
