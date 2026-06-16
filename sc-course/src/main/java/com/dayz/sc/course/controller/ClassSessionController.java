package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreateClassBarrageRequest;
import com.dayz.sc.course.model.dto.CreateClassSessionRequest;
import com.dayz.sc.course.model.dto.JoinClassSessionRequest;
import com.dayz.sc.course.model.dto.UpdateClassSessionRequest;
import com.dayz.sc.course.model.vo.ClassBarrageVO;
import com.dayz.sc.course.model.vo.ClassParticipantVO;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.LiveKitTokenVO;
import com.dayz.sc.course.service.ClassSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller for class sessions.
 */
@RestController
@RequestMapping("/api/class-sessions")
@RequiredArgsConstructor
public class ClassSessionController {

    private final ClassSessionService classSessionService;

    @PostMapping
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<UUID> createSession(
            @Valid @RequestBody CreateClassSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID sessionId = classSessionService.createSession(request, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(sessionId);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.updateSession(id, request, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publishSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.publishSession(id, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        classSessionService.deleteSession(id, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(null);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<PageResponse<ClassSessionVO>> listByCourse(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.listByCourse(courseId, page, size, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ClassSessionVO> getSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.getSession(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/join")
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    public ApiResponse<ClassParticipantVO> joinSession(
            @PathVariable UUID id,
            @Valid @RequestBody JoinClassSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.joinSession(id, request, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/{id}/live-token")
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    public ApiResponse<LiveKitTokenVO> liveToken(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.createLiveToken(id, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}/barrages/stream")
    public SseEmitter streamBarrages(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return classSessionService.streamBarrages(id, userId, JwtPrincipalResolver.role(jwt));
    }

    @PostMapping("/{id}/barrages")
    @RateLimited(maxRequests = 60, windowSeconds = 60)
    public ApiResponse<ClassBarrageVO> sendBarrage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateClassBarrageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.sendBarrage(id, request, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/{id}/barrages")
    public ApiResponse<PageResponse<ClassBarrageVO>> listBarrages(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(classSessionService.listBarrages(id, page, size, userId, JwtPrincipalResolver.role(jwt)));
    }
}
