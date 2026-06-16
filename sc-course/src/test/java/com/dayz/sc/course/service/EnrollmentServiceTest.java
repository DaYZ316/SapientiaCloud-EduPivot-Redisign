package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.repository.ClassSessionRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private StorageInternalClient storageInternalClient;

    @Mock
    private CourseEventPublisher courseEventPublisher;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(
                enrollmentRepository,
                courseRepository,
                storageInternalClient,
                courseEventPublisher,
                authInternalClient,
                courseTeacherRepository,
                classSessionRepository
        );
    }

    @Test
    void listCourseEnrollments_shouldAllowUnrelatedTeacherForPublicPublishedCourse() {
        UUID courseId = UUID.randomUUID();
        UUID primaryTeacherId = UUID.randomUUID();
        UUID unrelatedTeacherId = UUID.randomUUID();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(publicCourse(courseId, primaryTeacherId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, unrelatedTeacherId)).thenReturn(false);
        when(enrollmentRepository.findByCourseId(courseId, 1, 10)).thenReturn(new Page<Enrollment>(1, 10));

        enrollmentService.listCourseEnrollments(courseId, 1, 10, unrelatedTeacherId, 2);

        verify(enrollmentRepository).findByCourseId(eq(courseId), anyInt(), anyInt());
    }

    private Course publicCourse(UUID courseId, UUID teacherId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        course.setIsPublic(1);
        course.setStatus(CourseStatus.PUBLISHED.getCode());
        return course;
    }
}
