package com.dayz.sc.auth.controller;

import com.dayz.sc.auth.model.dto.UpdateUserRequest;
import com.dayz.sc.auth.model.dto.CompleteOnboardingRequest;
import com.dayz.sc.auth.model.dto.UserBasicInfo;
import com.dayz.sc.auth.model.dto.UserPageRequest;
import com.dayz.sc.auth.model.vo.LoginResponseVO;
import com.dayz.sc.auth.model.vo.UserProfileVO;
import com.dayz.sc.auth.service.UserManagementService;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.InternalUserProfile;
import com.dayz.sc.common.dashboard.DashboardUserSummary;
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
 * 绯荤粺鐢ㄦ埛鍒嗛〉鏌ヨ涓庤处鍙疯祫鏂欑淮鎶ゆ帴鍙?
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
    @RateLimited
    public ApiResponse<@NonNull UserProfileVO> updateCurrentUser(@Valid @RequestBody UpdateUserRequest request,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.updateCurrentUser(userId, request));
    }

    @PostMapping("/me/onboarding")
    @RateLimited
    public ApiResponse<@NonNull LoginResponseVO> completeOnboarding(@Valid @RequestBody CompleteOnboardingRequest request,
                                                                    @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.completeOnboarding(userId, request));
    }

    @GetMapping
    public ApiResponse<@NonNull PageResponse<@NonNull UserProfileVO>> pageUsers(
            UserPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ApiResponse.ok(userManagementService.pageUsers(request));
    }

    @PutMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull UserProfileVO> updateUser(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateUserRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ApiResponse.ok(userManagementService.updateUser(id, request));
    }

    @PutMapping("/{id}/reset-password")
    @RateLimited
    public ApiResponse<@NonNull Void> resetPassword(@PathVariable UUID id,
                                           @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        userManagementService.resetPassword(id);
        return ApiResponse.ok(null);
    }

    /**
     * 宸茬櫥褰曠敤鎴峰彲鐢細鎵归噺鑾峰彇鐢ㄦ埛鍩烘湰淇℃伅锛坉isplayName, avatarUrl锛?
     * 鐢ㄤ簬鍓嶇璁哄潧銆佽瘎璁虹瓑鍦烘櫙灞曠ず鐢ㄦ埛澶村儚鍜屾樀绉?
     */
    @GetMapping("/basic")
    public ApiResponse<@NonNull List<@NonNull UserBasicInfo>> getUsersBasicInfo(
            @RequestParam List<UUID> ids,
            @AuthenticationPrincipal Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.getUsersBasicInfo(ids));
    }

    /**
     * 鍐呴儴鎺ュ彛锛氭壒閲忚幏鍙栫敤鎴峰熀鏈俊鎭紙displayName, avatarUrl锛?
     * 鐢ㄤ簬鏈嶅姟闂撮€氫俊锛屽璇剧▼鏈嶅姟鑾峰彇鏁欏笀淇℃伅
     */
    @GetMapping("/internal/basic")
    public ApiResponse<@NonNull List<@NonNull UserBasicInfo>> getUsersBasicInfoInternal(@RequestParam List<UUID> ids) {
        return ApiResponse.ok(userManagementService.getUsersBasicInfo(ids));
    }

    @GetMapping("/internal/profile")
    public ApiResponse<@NonNull InternalUserProfile> getUserProfileInternal(@RequestParam UUID id) {
        return ApiResponse.ok(userManagementService.getInternalUserProfile(id));
    }

    @GetMapping("/internal/dashboard/summary")
    public ApiResponse<@NonNull DashboardUserSummary> getDashboardSummaryInternal() {
        return ApiResponse.ok(userManagementService.getDashboardSummary());
    }

    /**
     * 宸茬櫥褰曠敤鎴峰彲鐢細鎸?ID 鏌ョ湅鍏朵粬鐢ㄦ埛鍏紑璧勬枡
     */
    @GetMapping("/{id}")
    public ApiResponse<@NonNull UserProfileVO> getUser(@PathVariable UUID id,
                                                       @AuthenticationPrincipal Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.getUser(id));
    }


    /**
     * 鏁欏笀鍙皟鐢細鎸夎鑹叉煡璇㈢敤鎴峰垪琛紙浠呴檺 role=2 鏁欏笀锛?
     */
    @GetMapping("/teachers")
    public ApiResponse<@NonNull PageResponse<@NonNull UserProfileVO>> listTeachers(
            UserPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        var teacherRequest = new UserPageRequest(request.page(), request.size(), request.keyword(), request.status(), 2);
        return ApiResponse.ok(userManagementService.pageUsers(teacherRequest));
    }


    /**
     * 宸茬櫥褰曠敤鎴峰彲鐢細鏌ヨ鐢ㄦ埛鍒楄〃锛堥€氱煡閫変汉绛夊満鏅級
     */
    @GetMapping("/all")
    public ApiResponse<@NonNull PageResponse<@NonNull UserProfileVO>> listAllUsers(
            UserPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(userManagementService.pageUsers(request));
    }

    private void requireAdmin(Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (!SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }
}
