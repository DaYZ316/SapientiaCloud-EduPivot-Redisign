package com.dayz.sc.storage.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.storage.client.CourseAccessClient;
import com.dayz.sc.storage.model.dto.CreateUploadRequest;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.enums.StorageScopeType;
import com.dayz.sc.storage.model.enums.StorageUsage;
import com.dayz.sc.storage.model.enums.StorageVisibility;
import com.dayz.sc.storage.model.vo.CourseAccess;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Service
@RequiredArgsConstructor
public class StorageAuthorizationService {

    private final CourseAccessClient courseAccessClient;

    public void authorizeCreate(CreateUploadRequest request, UUID userId, Integer role) {
        switch (request.usage()) {
            case USER_AVATAR -> authorizeUserAvatar(request, userId, role);
            case COURSE_COVER, COURSE_FILE, COURSE_PUBLIC_FILE, COURSE_PRIVATE_FILE ->
                    requireCourseManager(request.scopeId());
            case FORUM_IMAGE -> requireCourseReader(request);
            case AI_FILE -> authorizeAiFile(request, userId, role);
            default -> throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }
    }

    public void authorizeRead(StorageObject object, UUID userId, Integer role) {
        StorageUsage usage = StorageUsage.valueOf(object.getUsage());
        if (isCourseScopedProtectedUsage(usage)) {
            CourseAccess access = courseAccess(object.getScopeId());
            boolean canRead = access != null && (access.canReadPublic() || access.canReadPrivate());
            if (canRead) {
                return;
            }
            throw new BusinessException(ErrorCodes.STORAGE_UNAUTHORIZED);
        }

        StorageVisibility visibility = StorageVisibility.valueOf(object.getVisibility());
        if (visibility == StorageVisibility.PUBLIC_READ) {
            return;
        }
        if (visibility == StorageVisibility.AUTHENTICATED) {
            return;
        }
        if (visibility == StorageVisibility.OWNER_PRIVATE) {
            boolean isOwner = object.getOwnerUserId().equals(userId);
            boolean isAdmin = SecurityUtils.isAdmin(role);
            if (isOwner || isAdmin) {
                return;
            }
        }
        if (visibility == StorageVisibility.COURSE_PRIVATE && canReadPrivateCourse(object.getScopeId())) {
            return;
        }
        throw new BusinessException(ErrorCodes.STORAGE_UNAUTHORIZED);
    }

    public void authorizeDelete(StorageObject object, UUID userId, Integer role) {
        StorageUsage usage = StorageUsage.valueOf(object.getUsage());
        if (SecurityUtils.isAdmin(role)) {
            return;
        }

        boolean isOwnerResource = usage == StorageUsage.USER_AVATAR
                || usage == StorageUsage.FORUM_IMAGE
                || usage == StorageUsage.AI_FILE;
        if (isOwnerResource && object.getOwnerUserId().equals(userId)) {
            return;
        }

        boolean isCourseResource = usage == StorageUsage.COURSE_COVER
                || usage == StorageUsage.COURSE_FILE
                || usage == StorageUsage.COURSE_PUBLIC_FILE
                || usage == StorageUsage.COURSE_PRIVATE_FILE;
        if (isCourseResource && canManageCourse(object.getScopeId())) {
            return;
        }
        throw new BusinessException(ErrorCodes.STORAGE_UNAUTHORIZED);
    }

    private void authorizeUserAvatar(CreateUploadRequest request, UUID userId, Integer role) {
        if (request.scopeType() != StorageScopeType.USER || request.scopeId() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid avatar scope");
        }
        if (!request.scopeId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private void authorizeAiFile(CreateUploadRequest request, UUID userId, Integer role) {
        if (request.scopeType() == StorageScopeType.USER) {
            if (request.scopeId() != null && !request.scopeId().equals(userId) && !SecurityUtils.isAdmin(role)) {
                throw new BusinessException(ErrorCodes.FORBIDDEN);
            }
            return;
        }
        if (request.scopeType() == StorageScopeType.COURSE) {
            requireCourseManager(request.scopeId());
            return;
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid AI file scope");
    }

    private void requireCourseReader(CreateUploadRequest request) {
        if (request.scopeType() != StorageScopeType.COURSE || request.scopeId() == null || !canReadPrivateCourse(request.scopeId())) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private void requireCourseManager(UUID courseId) {
        if (courseId == null || !canManageCourse(courseId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private boolean canManageCourse(UUID courseId) {
        CourseAccess access = courseAccess(courseId);
        return access != null && access.canManage();
    }

    private boolean canReadPrivateCourse(UUID courseId) {
        CourseAccess access = courseAccess(courseId);
        return access != null && access.canReadPrivate();
    }

    private boolean isCourseScopedProtectedUsage(StorageUsage usage) {
        return usage == StorageUsage.FORUM_IMAGE
                || usage == StorageUsage.COURSE_FILE
                || usage == StorageUsage.COURSE_PUBLIC_FILE
                || usage == StorageUsage.COURSE_PRIVATE_FILE;
    }

    private CourseAccess courseAccess(UUID courseId) {
        if (courseId == null) {
            return null;
        }
        ApiResponse<@NonNull CourseAccess> response = courseAccessClient.getAccess(courseId);
        if (response == null || response.data() == null || response.code() != ErrorCodes.SUCCESS.code()) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        return response.data();
    }
}
