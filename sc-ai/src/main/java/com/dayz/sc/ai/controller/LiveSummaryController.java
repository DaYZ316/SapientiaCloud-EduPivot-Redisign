package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.model.vo.LiveSummaryAudioTokenVO;
import com.dayz.sc.ai.model.vo.LiveSummarySessionVO;
import com.dayz.sc.ai.model.vo.LiveSummarySnapshotVO;
import com.dayz.sc.ai.service.LiveSummaryService;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * LiveSummaryController.
 *
 * @author DaYZ
 */
@RestController
@RequestMapping("/api/ai/live-summaries/class-sessions/{sessionId}")
@RequiredArgsConstructor
public class LiveSummaryController {

    private final LiveSummaryService liveSummaryService;

    @PostMapping("/start")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull LiveSummarySessionVO> start(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.start(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/resume/{summarySessionId}")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull LiveSummarySessionVO> resume(
            @PathVariable UUID sessionId,
            @PathVariable UUID summarySessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.resume(sessionId, summarySessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/stop")
    public ApiResponse<@NonNull LiveSummarySessionVO> stop(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.stop(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping
    public ApiResponse<@NonNull LiveSummarySessionVO> get(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.get(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping("/snapshots")
    public ApiResponse<@NonNull List<@NonNull LiveSummarySnapshotVO>> snapshots(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.listSnapshots(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @DeleteMapping("/snapshots/{snapshotId}")
    public ApiResponse<@NonNull List<@NonNull LiveSummarySnapshotVO>> deleteSnapshot(
            @PathVariable UUID sessionId,
            @PathVariable UUID snapshotId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.deleteSnapshot(sessionId, snapshotId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @DeleteMapping("/history-records/{summarySessionId}")
    public ApiResponse<@NonNull List<@NonNull LiveSummarySnapshotVO>> deleteHistoryRecord(
            @PathVariable UUID sessionId,
            @PathVariable UUID summarySessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.deleteHistoryRecord(sessionId, summarySessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @DeleteMapping
    public ApiResponse<@NonNull LiveSummarySessionVO> clear(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.clear(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @PostMapping("/audio-token")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull LiveSummaryAudioTokenVO> audioToken(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.issueAudioToken(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<@NonNull Flux<@NonNull ServerSentEvent<@NonNull String>>> stream(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(liveSummaryService.stream(sessionId, userId, JwtPrincipalResolver.role(jwt)));
    }
}
