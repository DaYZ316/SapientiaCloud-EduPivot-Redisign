package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.dto.EnrollRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.repository.ClassSessionRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final StorageInternalClient storageInternalClient;
    private final CourseEventPublisher courseEventPublisher;
    private final AuthInternalClient authInternalClient;
    private final CourseTeacherRepository courseTeacherRepository;
    private final ClassSessionRepository classSessionRepository;

    @Transactional(rollbackFor = Exception.class)
    public UUID enroll(EnrollRequest request, UUID studentId) {
        Course course = courseRepository.findByIdForUpdate(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (course.getStatus() != CourseStatus.PUBLISHED.getCode()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        Optional<Enrollment> existingEnrollment = enrollmentRepository.findByCourseIdAndStudentId(request.courseId(), studentId);
        if (existingEnrollment.isPresent()
                && existingEnrollment.get().getStatus() != EnrollmentStatus.DROPPED.getCode()) {
            throw new BusinessException(ErrorCodes.ENROLLMENT_ALREADY_EXISTS);
        }

        if (course.getMaxStudents() > 0) {
            long currentStudents = enrollmentRepository.countActiveByCourseId(request.courseId());
            if (currentStudents >= course.getMaxStudents()) {
                throw new BusinessException(ErrorCodes.ENROLLMENT_COURSE_FULL);
            }
        }

        if (existingEnrollment.isPresent()) {
            Enrollment enrollment = existingEnrollment.get();
            enrollment.setStatus(EnrollmentStatus.ACTIVE.getCode());
            enrollment.setEnrolledAt(Instant.now());
            enrollment.setCompletedAt(null);
            enrollmentRepository.update(enrollment);
            publishEnrollmentEvent(course, studentId, "ENROLLED");
            return enrollment.getId();
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(request.courseId());
        enrollment.setStudentId(studentId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE.getCode());
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setId(UuidV7Generator.generate());

        enrollmentRepository.save(enrollment);
        publishEnrollmentEvent(course, studentId, "ENROLLED");
        return enrollment.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void dropCourse(UUID enrollmentId, UUID studentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!enrollment.getStudentId().equals(studentId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        if (enrollment.getStatus() == EnrollmentStatus.DROPPED.getCode()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        enrollment.setStatus(EnrollmentStatus.DROPPED.getCode());
        enrollmentRepository.update(enrollment);

        courseRepository.findById(enrollment.getCourseId())
                .ifPresent(course -> publishEnrollmentEvent(course, studentId, "DROPPED"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(UUID enrollmentId, int status, UUID userId, Integer role) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        Course course = courseRepository.findById(enrollment.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        boolean isCourseTeacher = course.getTeacherId().equals(userId)
                || courseTeacherRepository.existsByCourseIdAndTeacherId(enrollment.getCourseId(), userId);
        if (!isCourseTeacher && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        EnrollmentStatus.fromCode(status);
        enrollment.setStatus(status);

        if (status == EnrollmentStatus.COMPLETED.getCode()) {
            enrollment.setCompletedAt(Instant.now());
        }

        enrollmentRepository.update(enrollment);
    }

    public PageResponse<@NonNull EnrollmentVO> listStudentEnrollments(UUID studentId, int page, int size) {
        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);
        Page<Enrollment> result = enrollmentRepository.findByStudentId(studentId, currentPage, pageSize);
        List<Enrollment> enrollments = result.getRecords();
        Map<UUID, Course> courses = loadCourses(enrollments);
        Map<UUID, String> coverUrls = loadCoverUrls(courses.values().stream().toList());
        Map<UUID, String> studentNames = loadStudentNames(enrollments);
        Map<UUID, Long> publishedClassSessionCounts = classSessionRepository.countPublishedByCourseIds(
                courses.keySet().stream().toList());

        List<EnrollmentVO> voList = enrollments.stream()
                .map(enrollment -> toEnrollmentVO(enrollment,
                        courses.get(enrollment.getCourseId()),
                        coverUrls,
                        studentNames,
                        publishedClassSessionCounts.getOrDefault(enrollment.getCourseId(), 0L)))
                .toList();

        return new PageResponse<>(voList, result.getTotal(), currentPage, pageSize);
    }

    public PageResponse<@NonNull EnrollmentVO> listCourseEnrollments(UUID courseId, int page, int size, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        boolean isCourseTeacher = course.getTeacherId().equals(userId)
                || courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
        if (!isCourseTeacher && !SecurityUtils.isAdmin(role) && !isPublicCourse(course)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);
        Page<Enrollment> result = enrollmentRepository.findByCourseId(courseId, currentPage, pageSize);
        List<Enrollment> enrollments = result.getRecords();

        Map<UUID, String> coverUrls = loadCoverUrls(List.of(course));
        Map<UUID, String> studentNames = loadStudentNames(enrollments);
        long publishedClassSessionCount = classSessionRepository.countPublishedByCourseIds(List.of(courseId))
                .getOrDefault(courseId, 0L);
        List<EnrollmentVO> voList = enrollments.stream()
                .map(enrollment -> toEnrollmentVO(enrollment, course, coverUrls, studentNames, publishedClassSessionCount))
                .toList();

        return new PageResponse<>(voList, result.getTotal(), currentPage, pageSize);
    }

    private boolean isPublicCourse(Course course) {
        return Objects.equals(course.getIsPublic(), 1);
    }

    private void publishEnrollmentEvent(Course course, UUID studentId, String action) {
        UserBasicInfo studentInfo = null;
        try {
            ApiResponse<@NonNull List<@NonNull UserBasicInfo>> resp = authInternalClient.getUsersBasicInfo(List.of(studentId));
            if (resp != null && resp.code() == 0 && resp.data() != null && !resp.data().isEmpty()) {
                studentInfo = resp.data().getFirst();
            }
        } catch (Exception ignored) {
        }
        String studentName = studentInfo != null && studentInfo.displayName() != null
                ? studentInfo.displayName() : "未知学生";
        courseEventPublisher.publishEnrollmentChanged(
                course.getId(), course.getTitle(),
                studentId, studentName,
                course.getTeacherId(), action);
    }

    private EnrollmentVO toEnrollmentVO(Enrollment enrollment, Course course, Map<UUID, String> coverUrls,
                                        Map<UUID, String> studentNames, long publishedClassSessionCount) {
        String coverUrl = course != null && course.getCoverFileId() != null
                ? coverUrls.getOrDefault(course.getCoverFileId(), course.getCoverUrl())
                : course != null ? course.getCoverUrl() : null;
        Integer totalClassHours = course != null ? course.getTotalClassHours() : null;
        return new EnrollmentVO(
                enrollment.getId(),
                enrollment.getCourseId(),
                course != null ? course.getTitle() : null,
                coverUrl,
                enrollment.getStudentId(),
                studentNames.getOrDefault(enrollment.getStudentId(), null),
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt(),
                totalClassHours,
                publishedClassSessionCount,
                CourseProgressCalculator.calculate(totalClassHours, publishedClassSessionCount)
        );
    }

    private Map<UUID, String> loadCoverUrls(List<Course> courses) {
        List<UUID> fileIds = courses.stream()
                .map(Course::getCoverFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<@NonNull Map<@NonNull UUID, @NonNull String>> response = storageInternalClient.getUrls(fileIds);
            if (response != null && response.code() == 0 && response.data() != null) {
                return response.data();
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }

    private Map<UUID, Course> loadCourses(List<Enrollment> enrollments) {
        List<UUID> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseRepository.findByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
    }


    private Map<UUID, String> loadStudentNames(List<Enrollment> enrollments) {
        List<UUID> studentIds = enrollments.stream()
                .map(Enrollment::getStudentId)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<@NonNull List<@NonNull UserBasicInfo>> response = authInternalClient.getUsersBasicInfo(studentIds);
            if (response != null && response.code() == 0 && response.data() != null) {
                return response.data().stream()
                        .filter(u -> u.displayName() != null)
                        .collect(Collectors.toMap(UserBasicInfo::id, UserBasicInfo::displayName, (a, b) -> a));
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }
}
