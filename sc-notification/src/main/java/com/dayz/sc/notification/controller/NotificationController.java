package com.dayz.sc.notification.controller;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.notification.model.dto.SendNotificationRequest;
import com.dayz.sc.notification.model.vo.NotificationVO;
import com.dayz.sc.notification.model.vo.UnreadCountVO;
import com.dayz.sc.notification.service.NotificationService;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 通知接口。
 * <p>
 * 角色校验通过 Gateway 传递的 X-User-Role 请求头实现。
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
     * 发送通知（仅管理员和教师可调用）。
     * 角色通过 Gateway 注入的 X-User-Role 请求头获取。
     */
    @PostMapping
    public ApiResponse<UUID> sendNotification(
            @Valid @RequestBody SendNotificationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID senderId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (role == null || (role != 0 && role != 2)) {
            throw new BusinessException(ErrorCodes.NOTIFICATION_SEND_FORBIDDEN);
        }
        UUID notificationId = notificationService.sendNotification(request, senderId);
        return ApiResponse.ok(notificationId);
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationVO>> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "false") boolean sentByMe) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<NotificationVO> response = notificationService.getNotifications(userId, page, size, type, sentByMe);
        return ApiResponse.ok(response);
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UnreadCountVO response = notificationService.getUnreadCount(userId);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.markAsRead(id, userId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer type) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.markAllAsRead(userId, type);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.deleteNotification(id, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/all")
    public ApiResponse<Void> deleteAllNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer type) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        notificationService.deleteAllNotifications(userId, type);
        return ApiResponse.ok(null);
    }

    /**
     * 撤回通知（仅发送者可操作，对所有接收者生效）。
     */
    @DeleteMapping("/{id}/recall")
    public ApiResponse<Void> recallNotification(
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
