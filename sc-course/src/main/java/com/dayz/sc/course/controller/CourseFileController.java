package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.BindCourseFileRequest;
import com.dayz.sc.course.model.vo.CourseFileVO;
import com.dayz.sc.course.service.CourseFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST 控制器
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/courses/{courseId}/files")
@RequiredArgsConstructor
public class CourseFileController {

    private final CourseFileService courseFileService;

    @PostMapping
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<CourseFileVO> bindFile(@PathVariable UUID courseId,
                                              @Valid @RequestBody BindCourseFileRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(courseFileService.bindFile(courseId, request, userId, role));
    }

    @GetMapping
    public ApiResponse<PageResponse<CourseFileVO>> listFiles(@PathVariable UUID courseId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size,
                                                             @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(courseFileService.listFiles(courseId, userId, role, page, size));
    }

    @DeleteMapping("/{courseFileId}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deleteFile(@PathVariable UUID courseId,
                                        @PathVariable UUID courseFileId,
                                        @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        courseFileService.deleteCourseFile(courseId, courseFileId, userId, role);
        return ApiResponse.ok(null);
    }
}
