package com.dayz.sc.course.controller;

import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.client.CourseAiContextClient;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @GetMapping("/search")
    public ApiResponse<@NonNull List<AgentSearchResult>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ROLE, required = false) String roleHeader,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(userIdHeader, jwt);
        Integer role = resolveRole(roleHeader, jwt);
        return ApiResponse.ok(aiCourseContextService.search(keyword, courseId, limit, userId, role));
    }

    @GetMapping("/resources")
    public ApiResponse<@NonNull List<AgentSearchResult>> resources(
            @RequestParam String keyword,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String courseTitle,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ROLE, required = false) String roleHeader,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(userIdHeader, jwt);
        Integer role = resolveRole(roleHeader, jwt);
        return ApiResponse.ok(aiCourseContextService.searchResources(
                keyword, courseId, courseTitle, types, limit, userId, role));
    }

    @GetMapping("/courses")
    public ApiResponse<@NonNull List<AgentSearchItem>> courses(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ROLE, required = false) String roleHeader,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(userIdHeader, jwt);
        Integer role = resolveRole(roleHeader, jwt);
        return ApiResponse.ok(aiCourseContextService.listCourses(scope, limit, userId, role));
    }

    @GetMapping("/chapters")
    public ApiResponse<@NonNull List<AgentSearchItem>> chapters(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String courseTitle,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = CourseAiContextClient.HEADER_USER_ROLE, required = false) String roleHeader,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(userIdHeader, jwt);
        Integer role = resolveRole(roleHeader, jwt);
        return ApiResponse.ok(aiCourseContextService.listChapters(courseId, courseTitle, limit, userId, role));
    }

    private UUID resolveUserId(String userIdHeader, Jwt jwt) {
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return UUID.fromString(userIdHeader);
        }
        return JwtPrincipalResolver.requireUserId(jwt);
    }

    private Integer resolveRole(String roleHeader, Jwt jwt) {
        if (roleHeader != null && !roleHeader.isBlank()) {
            return Integer.parseInt(roleHeader);
        }
        return JwtPrincipalResolver.role(jwt);
    }
}
