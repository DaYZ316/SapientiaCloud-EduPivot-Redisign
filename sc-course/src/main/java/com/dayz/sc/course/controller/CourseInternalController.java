package com.dayz.sc.course.controller;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.CourseAccessVO;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST 控制器
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/internal/courses")
@RequiredArgsConstructor
public class CourseInternalController {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;

    @GetMapping("/{courseId}/access")
    public ApiResponse<@NonNull CourseAccessVO> access(@PathVariable UUID courseId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        boolean admin = SecurityUtils.isAdmin(role);
        boolean primaryTeacher = course.getTeacherId().equals(userId);
        boolean courseTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
        boolean manager = admin || primaryTeacher;
        boolean enrolled = enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)
                .map(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                        || enrollment.getStatus() == EnrollmentStatus.COMPLETED.getCode())
                .orElse(false);

        return ApiResponse.ok(new CourseAccessVO(
                courseId,
                manager,
                Integer.valueOf(1).equals(course.getIsPublic()),
                manager || courseTeacher || enrolled,
                primaryTeacher
        ));
    }
}
