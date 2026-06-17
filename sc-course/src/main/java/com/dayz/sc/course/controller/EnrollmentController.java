package com.dayz.sc.course.controller;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.course.model.dto.EnrollRequest;
import com.dayz.sc.course.model.dto.UpdateEnrollmentStatusRequest;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.jspecify.annotations.NonNull;
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
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @RateLimited(maxRequests = 5)
    public ApiResponse<@NonNull UUID> enroll(
            @Valid @RequestBody EnrollRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (!SecurityUtils.isStudent(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Only students can enroll in courses");
        }

        UUID enrollmentId = enrollmentService.enroll(request, studentId);
        return ApiResponse.ok(enrollmentId);
    }

    @GetMapping("/my")
    public ApiResponse<@NonNull PageResponse<@NonNull EnrollmentVO>> listMyEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID studentId = JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<@NonNull EnrollmentVO> response = enrollmentService.listStudentEnrollments(studentId, page, size);
        return ApiResponse.ok(response);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<@NonNull PageResponse<@NonNull EnrollmentVO>> listCourseEnrollments(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        PageResponse<@NonNull EnrollmentVO> response = enrollmentService.listCourseEnrollments(courseId, page, size, userId, role);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}/status")
    @RateLimited
    public ApiResponse<@NonNull Void> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEnrollmentStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        enrollmentService.updateStatus(id, request.status(), userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> dropCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = JwtPrincipalResolver.requireUserId(jwt);
        enrollmentService.dropCourse(id, studentId);
        return ApiResponse.ok(null);
    }
}
