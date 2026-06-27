package com.dayz.sc.course.service;

import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.vo.CourseDetailVO;
import com.dayz.sc.course.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private CourseContentDeletionRepository courseContentDeletionRepository;

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
                courseContentDeletionRepository,
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

    @Test
    void deleteCourse_shouldDeleteContentTreeAndPublishEvent() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Course course = course(courseId, teacherId);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        courseService.deleteCourse(courseId, teacherId, 2);

        var inOrder = inOrder(courseContentDeletionRepository, courseRepository, courseTeacherRepository, courseEventPublisher);
        inOrder.verify(courseContentDeletionRepository).deleteCourseContent(courseId);
        inOrder.verify(courseRepository).deleteById(courseId);
        inOrder.verify(courseTeacherRepository).deleteByCourseId(courseId);
        inOrder.verify(courseEventPublisher).publishCourseDeleted(course);
    }

    @Test
    void courseDetailCacheEvict_shouldUseCourseIdInsteadOfAllEntries() throws Exception {
        Method create = CourseService.class.getMethod(
                "createCourse",
                com.dayz.sc.course.model.dto.CreateCourseRequest.class,
                UUID.class);
        Method update = CourseService.class.getMethod(
                "updateCourse",
                UUID.class,
                com.dayz.sc.course.model.dto.UpdateCourseRequest.class,
                UUID.class,
                Integer.class);
        Method delete = CourseService.class.getMethod("deleteCourse", UUID.class, UUID.class, Integer.class);

        assertThat(create.getAnnotation(CacheEvict.class)).isNull();
        assertThat(update.getAnnotation(CacheEvict.class).allEntries()).isFalse();
        assertThat(update.getAnnotation(CacheEvict.class).key()).isEqualTo("#courseId");
        assertThat(delete.getAnnotation(CacheEvict.class).allEntries()).isFalse();
        assertThat(delete.getAnnotation(CacheEvict.class).key()).isEqualTo("#courseId");
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
