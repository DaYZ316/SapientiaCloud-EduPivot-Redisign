package com.dayz.sc.storage.service;

import com.dayz.sc.storage.config.StorageProperties;
import com.dayz.sc.storage.model.dto.CreateUploadRequest;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.enums.StorageBucketType;
import com.dayz.sc.storage.model.enums.StorageScopeType;
import com.dayz.sc.storage.model.enums.StorageUsage;
import com.dayz.sc.storage.model.enums.StorageVisibility;
import com.dayz.sc.storage.repository.StorageObjectRepository;
import com.dayz.sc.storage.repository.StorageUploadSessionRepository;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * StorageServiceTest 相关定义。
 *
 * @author DaYZ
 * @since 2026-06-15
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioClient presignMinioClient;

    @Mock
    private StorageObjectRepository storageObjectRepository;

    @Mock
    private StorageUploadSessionRepository uploadSessionRepository;

    @Mock
    private StorageAuthorizationService authorizationService;

    @Captor
    private ArgumentCaptor<StorageObject> storageObjectCaptor;

    private StorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.getMinio().setEndpoint("http://localhost:9000");
        properties.getMinio().setExternalEndpoint("http://localhost:9000");
        storageService = new StorageService(
                minioClient,
                presignMinioClient,
                properties,
                storageObjectRepository,
                uploadSessionRepository,
                authorizationService
        );
    }

    @Test
    void createUpload_shouldUseCourseBucketAndProtectedVisibility_whenCourseFile() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        stubPostPolicy();

        storageService.createUpload(courseFileRequest(StorageUsage.COURSE_FILE, courseId, null), userId, 2);

        StorageObject object = capturedStorageObject();
        assertThat(object.getBucket()).isEqualTo("edupivot-course");
        assertThat(object.getUsage()).isEqualTo(StorageUsage.COURSE_FILE.name());
        assertThat(object.getVisibility()).isEqualTo(StorageVisibility.COURSE_PRIVATE.name());
    }

    @Test
    void createUpload_shouldUseCourseBucketAndProtectedVisibility_whenLegacyCourseFile() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        stubPostPolicy();

        storageService.createUpload(courseFileRequest(StorageUsage.COURSE_PUBLIC_FILE, courseId, StorageBucketType.COURSE_PUBLIC), userId, 2);

        StorageObject object = capturedStorageObject();
        assertThat(object.getBucket()).isEqualTo("edupivot-course");
        assertThat(object.getUsage()).isEqualTo(StorageUsage.COURSE_PUBLIC_FILE.name());
        assertThat(object.getVisibility()).isEqualTo(StorageVisibility.COURSE_PRIVATE.name());
    }

    @Test
    void createUpload_shouldUseCourseBucketAndProtectedVisibility_whenForumImage() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        stubPostPolicy();
        CreateUploadRequest request = new CreateUploadRequest(
                StorageUsage.FORUM_IMAGE,
                StorageScopeType.COURSE,
                courseId,
                "image.png",
                "image/png",
                1024L,
                null,
                null
        );

        storageService.createUpload(request, userId, 1);

        StorageObject object = capturedStorageObject();
        assertThat(object.getBucket()).isEqualTo("edupivot-course");
        assertThat(object.getUsage()).isEqualTo(StorageUsage.FORUM_IMAGE.name());
        assertThat(object.getVisibility()).isEqualTo(StorageVisibility.COURSE_PRIVATE.name());
    }

    @Test
    void createUpload_shouldRejectOversizedForumImage() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CreateUploadRequest request = new CreateUploadRequest(
                StorageUsage.FORUM_IMAGE,
                StorageScopeType.COURSE,
                courseId,
                "image.png",
                "image/png",
                5L * 1024 * 1024 + 1,
                null,
                null
        );

        assertThatThrownBy(() -> storageService.createUpload(request, userId, 1))
                .hasMessage("File is too large");
    }

    private void stubPostPolicy() throws Exception {
        when(presignMinioClient.getPresignedPostFormData(any(PostPolicy.class))).thenReturn(Map.of());
    }

    private StorageObject capturedStorageObject() {
        org.mockito.Mockito.verify(storageObjectRepository).save(storageObjectCaptor.capture());
        return storageObjectCaptor.getValue();
    }

    private CreateUploadRequest courseFileRequest(StorageUsage usage, UUID courseId, StorageBucketType bucketType) {
        return new CreateUploadRequest(
                usage,
                StorageScopeType.COURSE,
                courseId,
                "slides.pdf",
                "application/pdf",
                1024L,
                null,
                bucketType
        );
    }
}
