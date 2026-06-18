package com.dayz.sc.course.controller;

import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.service.AiCourseContextService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class AiInternalController {

    private final AiCourseContextService aiCourseContextService;

    @GetMapping("/context")
    public ApiResponse<@NonNull AiCourseContext> context(
            @RequestParam(required = false) UUID courseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(aiCourseContextService.buildContext(courseId, userId, role));
    }
}
