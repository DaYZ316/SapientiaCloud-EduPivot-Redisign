package com.dayz.sc.notification.controller;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.notification.model.dto.SendNotificationRequest;
import com.dayz.sc.notification.model.vo.NotificationVO;
import com.dayz.sc.notification.model.vo.UnreadCountVO;
import com.dayz.sc.notification.service.NotificationService;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 閫氱煡鎺ュ彛
 * <p>
 * 瑙掕壊鏍￠獙閫氳繃 Gateway 浼犻€掔殑 X-User-Role 璇锋眰澶村疄鐜?
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseEmitter sseEmitter;

    /**
     * 鍙戦€侀€氱煡锛堜粎绠＄悊鍛樺拰鏁欏笀鍙皟鐢級
     * 瑙掕壊閫氳繃 Gateway 娉ㄥ叆鐨?X-User-Role 璇锋眰澶磋幏鍙?
     */
    @PostMapping
    @RateLimited
    public ApiResponse<@NonNull UUID> sendNotification(
            @Valid @RequestBody SendNotificationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID senderId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (!SecurityUtils.isTeacherOrAdmin(role)) {
            throw new BusinessException(ErrorCodes.NOTIFICATION_SEND_FORBIDDEN);
        }
        UUID notificationId = notificationService.sendNotification(request, senderId);
        return ApiResponse.ok(notificationId);
    }

    @GetMapping
    public ApiResponse<@NonNull PageResponse<@NonNull NotificationVO>> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "false") boolean sentByMe) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<@NonNull NotificationVO> response = notificationService.getNotifications(userId, page, size, type, sentByMe);
        return ApiResponse.ok(response);
    }

    @GetMapping("/unread-count")
    public ApiResponse<@NonNull UnreadCountVO> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UnreadCountVO response = notificationService.getUnreadCount(userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/internal/dashboard/summary")
    public ApiResponse<@NonNull DashboardNotificationSummary> getDashboardSummaryInternal(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "false") boolean includeDistribution) {
        return ApiResponse.ok(notificationService.getDashboardSummary(userId, includeDistribution));
    }

    @PutMapping("/{id}/read")
    @RateLimited
    public ApiResponse<@NonNull Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.markAsRead(id, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    @RateLimited(maxRequests = 5)
    public ApiResponse<@NonNull Void> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer type) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.markAllAsRead(userId, type);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> deleteNotification(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.deleteNotification(id, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/all")
    @RateLimited(maxRequests = 5)
    public ApiResponse<@NonNull Void> deleteAllNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer type) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.deleteAllNotifications(userId, type);
        return ApiResponse.ok(null);
    }

    /**
     * 鎾ゅ洖閫氱煡锛堜粎鍙戦€佽€呭彲鎿嶄綔锛屽鎵€鏈夋帴鏀惰€呯敓鏁堬級
     */
    @DeleteMapping("/{id}/recall")
    @RateLimited
    public ApiResponse<@NonNull Void> recallNotification(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID senderId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        notificationService.recallNotification(id, senderId, role);
        return ApiResponse.ok(null);
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return sseEmitter.createEmitter(userId);
    }
}
