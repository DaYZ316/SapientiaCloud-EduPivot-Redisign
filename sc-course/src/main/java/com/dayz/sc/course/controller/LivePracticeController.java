package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreateLivePracticeRequest;
import com.dayz.sc.course.model.dto.SubmitLivePracticeAnswerRequest;
import com.dayz.sc.course.model.vo.LivePracticeGroupVO;
import com.dayz.sc.course.model.vo.LivePracticeSubmissionVO;
import com.dayz.sc.course.model.vo.LivePracticeWorkbookItemVO;
import com.dayz.sc.course.service.LivePracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * 随堂练习控制器
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@RestController
@RequiredArgsConstructor
public class LivePracticeController {

    private final LivePracticeService livePracticeService;

    @PostMapping("/api/class-sessions/{sessionId}/live-practices")
    @RateLimited
    public ApiResponse<@NonNull UUID> createLivePractice(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateLivePracticeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID groupId = livePracticeService.createLivePractice(sessionId, request, userId, JwtPrincipalResolver.role(jwt));
        return ApiResponse.ok(groupId);
    }

    @GetMapping("/api/class-sessions/{sessionId}/live-practices")
    public ApiResponse<@NonNull List<@NonNull LivePracticeGroupVO>> listByClassSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(livePracticeService.listByClassSession(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/api/live-practices/{groupId}")
    public ApiResponse<@NonNull LivePracticeGroupVO> getLivePractice(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(livePracticeService.getLivePractice(groupId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/api/live-practices/{groupId}/questions/{questionSnapshotId}/submissions")
    @RateLimited(maxRequests = 60)
    public ApiResponse<@NonNull LivePracticeSubmissionVO> submitAnswer(
            @PathVariable UUID groupId,
            @PathVariable UUID questionSnapshotId,
            @Valid @RequestBody SubmitLivePracticeAnswerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(livePracticeService.submitAnswer(groupId, questionSnapshotId, request, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/api/courses/{courseId}/live-practice-workbook")
    public ApiResponse<@NonNull List<@NonNull LivePracticeWorkbookItemVO>> getStudentWorkbook(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(livePracticeService.getStudentWorkbook(courseId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/api/courses/{courseId}/live-practices/teacher")
    public ApiResponse<@NonNull List<@NonNull LivePracticeGroupVO>> getTeacherPractices(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(livePracticeService.getTeacherPractices(courseId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping(value = "/api/live-practices/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return livePracticeService.subscribe(userId);
    }
}
