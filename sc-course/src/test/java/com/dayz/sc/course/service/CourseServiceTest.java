package com.dayz.sc.course.service;

import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.vo.CourseDetailVO;
import com.dayz.sc.course.repository.ClassSessionRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private StorageInternalClient storageInternalClient;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private CourseEventPublisher courseEventPublisher;

    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(
                courseRepository,
                courseTeacherRepository,
                classSessionRepository,
                enrollmentRepository,
                storageInternalClient,
                authInternalClient,
                courseEventPublisher
        );
    }

    @Test
    void getCourseDetail_shouldIncludeCourseProgressStats() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, teacherId)));
        when(enrollmentRepository.countActiveByCourseId(courseId)).thenReturn(31L);
        when(courseTeacherRepository.findTeacherIdsByCourseId(courseId)).thenReturn(List.of());
        when(classSessionRepository.countPublishedByCourseIds(List.of(courseId))).thenReturn(Map.of(courseId, 31L));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, viewerId)).thenReturn(Optional.empty());

        CourseDetailVO detail = courseService.getCourseDetail(courseId, viewerId);

        assertThat(detail.publishedClassSessionCount()).isEqualTo(31);
        assertThat(detail.courseProgress()).isEqualTo(48);
    }

    private Course course(UUID courseId, UUID teacherId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Music Technology");
        course.setTeacherId(teacherId);
        course.setLevel(1);
        course.setIsPublic(1);
        course.setMaxStudents(120);
        course.setTotalClassHours(64);
        course.setStatus(CourseStatus.PUBLISHED.getCode());
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }
}
