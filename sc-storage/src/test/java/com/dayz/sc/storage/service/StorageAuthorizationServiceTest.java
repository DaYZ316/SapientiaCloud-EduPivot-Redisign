package com.dayz.sc.storage.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.storage.client.CourseAccessClient;
import com.dayz.sc.storage.model.dto.CreateUploadRequest;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.enums.StorageScopeType;
import com.dayz.sc.storage.model.enums.StorageUsage;
import com.dayz.sc.storage.model.enums.StorageVisibility;
import com.dayz.sc.storage.model.vo.CourseAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageAuthorizationServiceTest {

    @Mock
    private CourseAccessClient courseAccessClient;

    private StorageAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new StorageAuthorizationService(courseAccessClient);
    }

    @Test
    void authorizeRead_shouldAllowCourseFile_whenCourseIsPublicReadable() {
        UUID courseId = UUID.randomUUID();
        StorageObject object = courseObject(StorageUsage.COURSE_FILE, courseId);
        when(courseAccessClient.getAccess(courseId))
                .thenReturn(ApiResponse.ok(new CourseAccess(courseId, false, true, false, false)));

        authorizationService.authorizeRead(object, UUID.randomUUID(), 1);
    }

    @Test
    void authorizeRead_shouldDenyCourseFile_whenCourseCannotBeRead() {
        UUID courseId = UUID.randomUUID();
        StorageObject object = courseObject(StorageUsage.COURSE_FILE, courseId);
        when(courseAccessClient.getAccess(courseId))
                .thenReturn(ApiResponse.ok(new CourseAccess(courseId, false, false, false, false)));

        assertThatThrownBy(() -> authorizationService.authorizeRead(object, UUID.randomUUID(), 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void authorizeRead_shouldAllowForumImage_whenCourseIsPublicReadable() {
        UUID courseId = UUID.randomUUID();
        StorageObject object = courseObject(StorageUsage.FORUM_IMAGE, courseId);
        when(courseAccessClient.getAccess(courseId))
                .thenReturn(ApiResponse.ok(new CourseAccess(courseId, false, true, false, false)));

        authorizationService.authorizeRead(object, UUID.randomUUID(), 1);
    }

    @Test
    void authorizeCreate_shouldAllowCourseFile_whenUserCanManageCourse() {
        UUID courseId = UUID.randomUUID();
        when(courseAccessClient.getAccess(courseId))
                .thenReturn(ApiResponse.ok(new CourseAccess(courseId, true, true, true, false)));

        authorizationService.authorizeCreate(courseUpload(StorageUsage.COURSE_FILE, courseId), UUID.randomUUID(), 1);
    }

    @Test
    void authorizeCreate_shouldDenyCourseFile_whenUserCannotManageCourse() {
        UUID courseId = UUID.randomUUID();
        when(courseAccessClient.getAccess(courseId))
                .thenReturn(ApiResponse.ok(new CourseAccess(courseId, false, true, true, false)));

        assertThatThrownBy(() -> authorizationService.authorizeCreate(courseUpload(StorageUsage.COURSE_FILE, courseId), UUID.randomUUID(), 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void authorizeCreate_shouldAllowForumImage_whenUserCanReadPrivateCourse() {
        UUID courseId = UUID.randomUUID();
        when(courseAccessClient.getAccess(courseId))
                .thenReturn(ApiResponse.ok(new CourseAccess(courseId, false, true, true, false)));

        authorizationService.authorizeCreate(courseUpload(StorageUsage.FORUM_IMAGE, courseId), UUID.randomUUID(), 1);
    }

    private StorageObject courseObject(StorageUsage usage, UUID courseId) {
        StorageObject object = new StorageObject();
        object.setId(UUID.randomUUID());
        object.setUsage(usage.name());
        object.setVisibility(StorageVisibility.COURSE_PRIVATE.name());
        object.setScopeType(StorageScopeType.COURSE.name());
        object.setScopeId(courseId);
        object.setOwnerUserId(UUID.randomUUID());
        return object;
    }

    private CreateUploadRequest courseUpload(StorageUsage usage, UUID courseId) {
        return new CreateUploadRequest(
                usage,
                StorageScopeType.COURSE,
                courseId,
                "file.pdf",
                "application/pdf",
                1024L,
                null,
                null
        );
    }
}
