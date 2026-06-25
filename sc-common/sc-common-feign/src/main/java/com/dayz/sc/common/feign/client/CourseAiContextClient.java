package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.ClassSessionAiAccess;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(
        contextId = "courseAiContextClient",
        name = "sc-course",
        path = "/internal/ai",
        fallback = CourseAiContextClientFallback.class)
public interface CourseAiContextClient {

    String HEADER_USER_ID = "X-User-Id";
    String HEADER_USER_ROLE = "X-User-Role";

    @GetMapping("/context")
    ApiResponse<@NonNull AiCourseContext> getContext(@RequestParam(value = "courseId", required = false) UUID courseId);

    @GetMapping("/search")
    ApiResponse<@NonNull List<AgentSearchResult>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    @GetMapping("/resources")
    ApiResponse<@NonNull List<AgentSearchResult>> resources(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "courseTitle", required = false) String courseTitle,
            @RequestParam(value = "types", required = false) List<String> types,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    @GetMapping("/courses")
    ApiResponse<@NonNull List<AgentSearchItem>> courses(
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    @GetMapping("/chapters")
    ApiResponse<@NonNull List<AgentSearchItem>> chapters(
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "courseTitle", required = false) String courseTitle,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    @GetMapping("/class-session-access")
    ApiResponse<@NonNull ClassSessionAiAccess> classSessionAccess(
            @RequestParam("classSessionId") UUID classSessionId,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);
}
