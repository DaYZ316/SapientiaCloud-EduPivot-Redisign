package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreatePracticeSessionRequest;
import com.dayz.sc.course.model.dto.SubmitAnswerRequest;
import com.dayz.sc.course.model.vo.PracticeAnswerVO;
import com.dayz.sc.course.model.vo.PracticeSessionVO;
import com.dayz.sc.course.service.PracticeSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST 控制器
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@RestController
@RequestMapping("/api/practice-sessions")
@RequiredArgsConstructor
public class PracticeSessionController {

    private final PracticeSessionService practiceSessionService;

    @PostMapping
    @RateLimited
    public ApiResponse<@NonNull UUID> createPracticeSession(
            @Valid @RequestBody CreatePracticeSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID sessionId = practiceSessionService.createPracticeSession(request, userId);
        return ApiResponse.ok(sessionId);
    }

    @GetMapping("/{id}")
    public ApiResponse<@NonNull PracticeSessionVO> getPracticeSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        PracticeSessionVO session = practiceSessionService.getPracticeSession(id, userId);
        return ApiResponse.ok(session);
    }

    @PostMapping("/{id}/answers")
    @RateLimited(maxRequests = 30)
    public ApiResponse<@NonNull PracticeAnswerVO> submitAnswer(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        PracticeAnswerVO answer = practiceSessionService.submitAnswer(id, request, userId);
        return ApiResponse.ok(answer);
    }

    @PutMapping("/{id}/complete")
    public ApiResponse<@NonNull Void> completePracticeSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        practiceSessionService.completePracticeSession(id, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/my")
    public ApiResponse<@NonNull List<@NonNull PracticeSessionVO>> getMyPracticeHistory(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        List<PracticeSessionVO> history = practiceSessionService.getMyPracticeHistory(userId);
        return ApiResponse.ok(history);
    }

    @GetMapping("/bank/{bankId}/stats")
    public ApiResponse<@NonNull PracticeSessionVO> getBankPracticeStats(@PathVariable UUID bankId) {
        PracticeSessionVO stats = practiceSessionService.getBankPracticeStats(bankId);
        return ApiResponse.ok(stats);
    }
}
