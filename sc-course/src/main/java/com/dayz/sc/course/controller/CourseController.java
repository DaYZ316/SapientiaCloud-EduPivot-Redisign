package com.dayz.sc.course.controller;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreateCourseRequest;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.dto.UpdateCourseRequest;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<UUID> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID teacherId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (role == null || (role != 0 && role != 2)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Only teachers can create courses");
        }

        UUID courseId = courseService.createCourse(request, teacherId);
        return ApiResponse.ok(courseId);
    }

    @GetMapping
    public ApiResponse<PageResponse<CourseVO>> listCourses(CoursePageRequest request) {
        PageResponse<CourseVO> response = courseService.listCourses(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseVO> getCourse(@PathVariable UUID id) {
        CourseVO course = courseService.getCourse(id);
        return ApiResponse.ok(course);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        courseService.updateCourse(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        courseService.deleteCourse(id, userId, role);
        return ApiResponse.ok(null);
    }

    @GetMapping("/teacher")
    public ApiResponse<PageResponse<CourseVO>> listTeacherCourses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID teacherId = JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<CourseVO> response = courseService.listTeacherCourses(teacherId, role, page, size);
        return ApiResponse.ok(response);
    }
}
