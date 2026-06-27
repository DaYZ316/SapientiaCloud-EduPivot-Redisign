package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * CourseContentAccessService.
 *
 * @author DaYZ
 */
@Service
@RequiredArgsConstructor
public class CourseContentAccessService {

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;

    public boolean canReadCourseContent(UUID courseId, @Nullable UUID userId, @Nullable Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        return canReadCourseContent(course, userId, role);
    }

    public boolean canReadCourseContent(Course course, @Nullable UUID userId, @Nullable Integer role) {
        if (isPublicPublishedCourse(course)) {
            return true;
        }
        return userId != null && canReadPrivateCourseContent(course, userId, role);
    }

    public void requireCourseContentAccess(UUID courseId, @Nullable UUID userId, @Nullable Integer role) {
        if (!canReadCourseContent(courseId, userId, role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    public void requireCourseContentAccess(Course course, @Nullable UUID userId, @Nullable Integer role) {
        if (!canReadCourseContent(course, userId, role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private boolean canReadPrivateCourseContent(Course course, UUID userId, @Nullable Integer role) {
        if (SecurityUtils.isAdmin(role)
                || course.getTeacherId().equals(userId)
                || courseTeacherRepository.existsByCourseIdAndTeacherId(course.getId(), userId)) {
            return true;
        }
        return enrollmentRepository.findByCourseIdAndStudentId(course.getId(), userId)
                .map(this::activeOrCompleted)
                .orElse(false);
    }

    private boolean isPublicPublishedCourse(Course course) {
        return Integer.valueOf(1).equals(course.getIsPublic())
                && Integer.valueOf(CourseStatus.PUBLISHED.getCode()).equals(course.getStatus());
    }

    private boolean activeOrCompleted(Enrollment enrollment) {
        return enrollment.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                || enrollment.getStatus() == EnrollmentStatus.COMPLETED.getCode();
    }
}
