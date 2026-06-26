package com.dayz.sc.course.service;

import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.course.model.dto.BindCourseFileRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.CourseFile;
import com.dayz.sc.course.model.enums.CourseFileVisibility;
import com.dayz.sc.course.repository.CourseFileRepository;
import com.dayz.sc.course.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseFileServiceTest 相关定义
 *
 * @author DaYZ
 * @since 2026-06-15
 */
@ExtendWith(MockitoExtension.class)
class CourseFileServiceTest {

    @Mock
    private CourseFileRepository courseFileRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseContentAccessService courseContentAccessService;

    @Mock
    private StorageInternalClient storageInternalClient;

    @Captor
    private ArgumentCaptor<CourseFile> courseFileCaptor;

    private CourseFileService courseFileService;

    @BeforeEach
    void setUp() {
        courseFileService = new CourseFileService(
                courseFileRepository,
                courseRepository,
                courseContentAccessService,
                storageInternalClient
        );
    }

    @Test
    void bindFile_shouldUsePublicVisibility_whenCourseIsPublic() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Course course = course(courseId, userId, 1);
        StorageObjectInfo file = storageFile(fileId, courseId, "COURSE_FILE");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(file));
        when(storageInternalClient.getUrls(anyList())).thenReturn(ApiResponse.ok(Map.of(fileId, "https://example.test/file")));

        courseFileService.bindFile(courseId, new BindCourseFileRequest(fileId, "Slides", null), userId, 2);

        verify(courseFileRepository).save(courseFileCaptor.capture());
        assertThat(courseFileCaptor.getValue().getVisibility()).isEqualTo(CourseFileVisibility.PUBLIC.name());
    }

    @Test
    void bindFile_shouldUsePrivateVisibility_whenCourseIsPrivate() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Course course = course(courseId, userId, 0);
        StorageObjectInfo file = storageFile(fileId, courseId, "COURSE_FILE");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(file));
        when(storageInternalClient.getUrls(anyList())).thenReturn(ApiResponse.ok(Map.of(fileId, "https://example.test/file")));

        courseFileService.bindFile(courseId, new BindCourseFileRequest(fileId, "Slides", null), userId, 2);

        verify(courseFileRepository).save(courseFileCaptor.capture());
        assertThat(courseFileCaptor.getValue().getVisibility()).isEqualTo(CourseFileVisibility.PRIVATE.name());
    }

    @Test
    void bindFile_shouldAcceptLegacyCourseFileUsage() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Course course = course(courseId, userId, 1);
        StorageObjectInfo file = storageFile(fileId, courseId, "COURSE_PUBLIC_FILE");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(file));
        when(storageInternalClient.getUrls(anyList())).thenReturn(ApiResponse.ok(Map.of(fileId, "https://example.test/file")));

        courseFileService.bindFile(courseId, new BindCourseFileRequest(fileId, "Slides", null), userId, 2);

        verify(courseFileRepository).save(courseFileCaptor.capture());
        assertThat(courseFileCaptor.getValue().getVisibility()).isEqualTo(CourseFileVisibility.PUBLIC.name());
    }

    private Course course(UUID courseId, UUID teacherId, int isPublic) {
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        course.setIsPublic(isPublic);
        return course;
    }

    private StorageObjectInfo storageFile(UUID fileId, UUID courseId, String usage) {
        return new StorageObjectInfo(
                fileId,
                "slides.pdf",
                "application/pdf",
                1024L,
                usage,
                "COURSE_PRIVATE",
                "COURSE",
                courseId,
                "READY",
                UUID.randomUUID(),
                Instant.now()
        );
    }
}
