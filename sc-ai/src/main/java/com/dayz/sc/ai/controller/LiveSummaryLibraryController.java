package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.model.vo.LiveSummaryRecordVO;
import com.dayz.sc.ai.service.LiveSummaryService;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * LiveSummaryLibraryController.
 *
 * @author DaYZ
 */
@RestController
@RequestMapping("/api/ai/live-summaries")
@RequiredArgsConstructor
public class LiveSummaryLibraryController {

    private final LiveSummaryService liveSummaryService;

    @GetMapping
    public ApiResponse<@NonNull PageResponse<@NonNull LiveSummaryRecordVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        return ApiResponse.ok(liveSummaryService.listVisibleSummaries(
                userId, JwtPrincipalResolver.role(jwt), page, size));
    }
}
