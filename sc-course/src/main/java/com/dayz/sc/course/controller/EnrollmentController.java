package com.dayz.sc.course.controller;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.EnrollRequest;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @RateLimited(maxRequests = 5, windowSeconds = 60)
    public ApiResponse<UUID> enroll(
            @Valid @RequestBody EnrollRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        if (role == null || role != 1) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "Only students can enroll in courses");
        }

        UUID enrollmentId = enrollmentService.enroll(request, studentId);
        return ApiResponse.ok(enrollmentId);
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<EnrollmentVO>> listMyEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID studentId = JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<EnrollmentVO> response = enrollmentService.listStudentEnrollments(studentId, page, size);
        return ApiResponse.ok(response);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<PageResponse<EnrollmentVO>> listCourseEnrollments(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        PageResponse<EnrollmentVO> response = enrollmentService.listCourseEnrollments(courseId, page, size, userId, role);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Integer> body,
            @AuthenticationPrincipal Jwt jwt) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResponse.fail(400, "Status is required");
        }

        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        enrollmentService.updateStatus(id, status, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> dropCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = JwtPrincipalResolver.requireUserId(jwt);
        enrollmentService.dropCourse(id, studentId);
        return ApiResponse.ok(null);
    }
}
