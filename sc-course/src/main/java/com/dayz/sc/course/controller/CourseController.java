package com.dayz.sc.course.controller;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.dto.CreateCourseRequest;
import com.dayz.sc.course.model.dto.UpdateCourseRequest;
import com.dayz.sc.course.model.vo.CourseDetailVO;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @RateLimited
    public ApiResponse<@NonNull UUID> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID teacherId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (!SecurityUtils.isTeacherOrAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Only teachers can create courses");
        }

        UUID courseId = courseService.createCourse(request, teacherId);
        return ApiResponse.ok(courseId);
    }

    @GetMapping
    public ApiResponse<@NonNull PageResponse<@NonNull CourseVO>> listCourses(CoursePageRequest request) {
        PageResponse<@NonNull CourseVO> response = courseService.listCourses(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<@NonNull CourseDetailVO> getCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = jwt != null ? JwtPrincipalResolver.userId(jwt) : null;
        CourseDetailVO detail = courseService.getCourseDetail(id, userId);
        return ApiResponse.ok(detail);
    }

    @PutMapping("/{id}")
    public ApiResponse<@NonNull Void> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        courseService.updateCourse(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<@NonNull Void> deleteCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        courseService.deleteCourse(id, userId, role);
        return ApiResponse.ok(null);
    }

    @GetMapping("/teacher")
    public ApiResponse<@NonNull PageResponse<@NonNull CourseVO>> listTeacherCourses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID teacherId = JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<@NonNull CourseVO> response = courseService.listTeacherCourses(teacherId, role, page, size);
        return ApiResponse.ok(response);
    }
}
