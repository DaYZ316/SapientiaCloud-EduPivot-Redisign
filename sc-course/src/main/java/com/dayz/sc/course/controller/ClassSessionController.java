package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreateClassBarrageRequest;
import com.dayz.sc.course.model.dto.CreateClassSessionRequest;
import com.dayz.sc.course.model.dto.JoinClassSessionRequest;
import com.dayz.sc.course.model.dto.UpdateClassSessionRequest;
import com.dayz.sc.course.model.vo.*;
import com.dayz.sc.course.service.ClassSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for class sessions.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@RestController
@RequestMapping("/api/class-sessions")
@RequiredArgsConstructor
public class ClassSessionController {

    private final ClassSessionService classSessionService;

    @PostMapping
    @RateLimited
    public ApiResponse<@NonNull UUID> createSession(
            @Valid @RequestBody CreateClassSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID sessionId = classSessionService.createSession(request, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(sessionId);
    }

    @PutMapping("/{id}")
    public ApiResponse<@NonNull Void> updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.updateSession(id, request, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<@NonNull Void> publishSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.publishSession(id, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<@NonNull Void> deleteSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.deleteSession(id, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<@NonNull PageResponse<@NonNull ClassSessionVO>> listByCourse(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.listByCourse(courseId, page, size, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}")
    public ApiResponse<@NonNull ClassSessionVO> getSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.getSession(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/join")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull ClassParticipantVO> joinSession(
            @PathVariable UUID id,
            @Valid @RequestBody JoinClassSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.joinSession(id, request, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}/participants")
    public ApiResponse<@NonNull List<@NonNull ClassParticipantVO>> listParticipants(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.listParticipants(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @DeleteMapping("/{id}/participants/me")
    public ApiResponse<@NonNull Void> leaveSeat(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.leaveSeat(id, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/seat-sync-token")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull ClassSeatSyncTokenVO> seatSyncToken(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.createSeatSyncToken(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/live-token")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull LiveKitTokenVO> liveToken(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.createLiveToken(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/live/start")
    public ApiResponse<@NonNull ClassSessionVO> startLive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.startLive(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/live/pause")
    public ApiResponse<@NonNull ClassSessionVO> pauseLive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.pauseLive(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/live/heartbeat")
    public ApiResponse<@NonNull Void> heartbeatLive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.heartbeatLive(id, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/live/resume")
    public ApiResponse<@NonNull ClassSessionVO> resumeLive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.resumeLive(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/live/stop")
    public ApiResponse<@NonNull ClassSessionVO> stopLive(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.stopLive(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}/barrages/stream")
    public SseEmitter streamBarrages(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return classSessionService.streamBarrages(id, userId, JwtPrincipalResolver.role(jwt));
    }

    @PostMapping("/{id}/barrages")
    @RateLimited(maxRequests = 60)
    public ApiResponse<@NonNull ClassBarrageVO> sendBarrage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateClassBarrageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.sendBarrage(id, request, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}/barrages")
    public ApiResponse<@NonNull PageResponse<@NonNull ClassBarrageVO>> listBarrages(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.listBarrages(id, page, size, userId, JwtPrincipalResolver.role(jwt)));
    }
}
