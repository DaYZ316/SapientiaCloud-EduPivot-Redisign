package com.dayz.sc.storage.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.storage.config.StorageProperties;
import com.dayz.sc.storage.repository.StorageObjectRepository;
import com.dayz.sc.storage.repository.StorageUploadSessionRepository;
import com.dayz.sc.storage.model.dto.CreateUploadRequest;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.entity.StorageUploadSession;
import com.dayz.sc.storage.model.enums.*;
import com.dayz.sc.storage.model.vo.DownloadUrlResponse;
import com.dayz.sc.storage.model.vo.FileAsset;
import com.dayz.sc.storage.model.vo.StorageObjectInfo;
import com.dayz.sc.storage.model.vo.UploadTicket;
import com.dayz.sc.storage.util.StorageObjectKeyBuilder;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class StorageService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> COURSE_FILE_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/zip",
            "application/x-zip-compressed",
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> AI_FILE_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json"
    );

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final StorageProperties storageProperties;
    private final StorageObjectRepository storageObjectRepository;
    private final StorageUploadSessionRepository uploadSessionRepository;
    private final StorageAuthorizationService authorizationService;

    public StorageService(MinioClient minioClient,
                          @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                          StorageProperties storageProperties,
                          StorageObjectRepository storageObjectRepository,
                          StorageUploadSessionRepository uploadSessionRepository,
                          StorageAuthorizationService authorizationService) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.storageProperties = storageProperties;
        this.storageObjectRepository = storageObjectRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadTicket createUpload(CreateUploadRequest request, UUID userId, Integer role) {
        authorizationService.authorizeCreate(request, userId, role);
        FilePolicy policy = filePolicy(request.usage());
        String contentType = normalizeContentType(request.contentType());
        validateContentType(contentType, policy.allowedTypes());
        if (request.sizeBytes() > policy.maxSizeBytes()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "File is too large");
        }

        UUID objectId = UuidV7Generator.generate();
        UUID scopeId = normalizeScopeId(request, userId);
        String objectKey = StorageObjectKeyBuilder.finalKey(request.usage(), scopeId, objectId, request.fileName(), contentType);
        String tempKey = StorageObjectKeyBuilder.tempKey(objectId, request.fileName(), contentType);
        Instant expiresAt = Instant.now().plusSeconds(storageProperties.getExpiry().getUploadMinutes() * 60L);

        StorageObject object = new StorageObject();
        object.setId(objectId);
        object.setBucket(policy.bucket());
        object.setObjectKey(objectKey);
        object.setOriginalFilename(safeFilename(request.fileName()));
        object.setContentType(contentType);
        object.setSizeBytes(request.sizeBytes());
        object.setSha256(normalizeBlank(request.sha256()));
        object.setUsage(request.usage().name());
        object.setVisibility(policy.visibility().name());
        object.setOwnerUserId(userId);
        object.setScopeType(request.scopeType().name());
        object.setScopeId(scopeId);
        object.setStatus(StorageObjectStatus.PENDING.name());
        storageObjectRepository.save(object);

        StorageUploadSession session = new StorageUploadSession();
        session.setId(UuidV7Generator.generate());
        session.setObjectId(objectId);
        session.setMethod("POST");
        session.setExpiresAt(expiresAt);
        session.setMaxSizeBytes(policy.maxSizeBytes());
        session.setAllowedContentType(contentType);
        session.setStatus(UploadSessionStatus.PENDING.name());
        uploadSessionRepository.save(session);

        Map<String, String> formData = createPostPolicy(policy.bucket(), tempKey, contentType, policy.maxSizeBytes());
        String uploadUrl = stripTrailingSlash(externalEndpoint()) + "/" + policy.bucket();
        return new UploadTicket(objectId, uploadUrl, "POST", formData, Map.of(), expiresAt, policy.maxSizeBytes());
    }

    @Transactional(rollbackFor = Exception.class)
    public FileAsset completeUpload(UUID objectId, UUID userId, Integer role) {
        StorageObject object = requireObject(objectId);
        if (!StorageObjectStatus.PENDING.name().equals(object.getStatus())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Upload is not pending");
        }
        if (!object.getOwnerUserId().equals(userId)) {
            authorizationService.authorizeDelete(object, userId, role);
        }

        StorageUploadSession session = requirePendingSession(objectId);
        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(UploadSessionStatus.EXPIRED.name());
            uploadSessionRepository.update(session);
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Upload ticket expired");
        }

        try {
            String tempKey = StorageObjectKeyBuilder.tempKey(objectId, object.getOriginalFilename(), object.getContentType());

            // 1. 验证临时文件存在
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(object.getBucket())
                    .object(tempKey)
                    .build());
            if (stat.size() > session.getMaxSizeBytes()) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Uploaded file is too large");
            }
            String statContentType = normalizeContentType(stat.contentType());
            if (StringUtils.hasText(statContentType)
                    && !"application/octet-stream".equals(statContentType)
                    && !statContentType.equals(session.getAllowedContentType())) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Uploaded content type mismatch");
            }

            // 2. 复制到最终路径
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(object.getBucket())
                    .object(object.getObjectKey())
                    .source(SourceObject.builder()
                            .bucket(object.getBucket())
                            .object(tempKey)
                            .build())
                    .build());

            // 3. 删除临时文件
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(object.getBucket())
                    .object(tempKey)
                    .build());

            // 4. 标记就绪
            object.setSizeBytes(stat.size());
            object.setEtag(stripQuotes(stat.etag()));
            object.setStatus(StorageObjectStatus.READY.name());
            object.setUploadedAt(Instant.now());
            storageObjectRepository.update(object);

            session.setStatus(UploadSessionStatus.COMPLETED.name());
            session.setCompletedAt(Instant.now());
            uploadSessionRepository.update(session);
            return toFileAsset(object, true);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodes.STORAGE_UPLOAD_FAILED, "Uploaded object was not found");
        }
    }

    public DownloadUrlResponse createDownloadUrl(UUID objectId, UUID userId, Integer role) {
        StorageObject object = requireReadyObject(objectId);
        authorizationService.authorizeRead(object, userId, role);
        if (hasStablePublicUrl(object)) {
            return new DownloadUrlResponse(publicObjectUrl(object), null);
        }
        Instant expiresAt = Instant.now().plusSeconds(storageProperties.getExpiry().getDownloadMinutes() * 60L);
        return new DownloadUrlResponse(presignedGetUrl(object), expiresAt);
    }

    public FileAsset getFile(UUID objectId, UUID userId, Integer role) {
        StorageObject object = requireReadyObject(objectId);
        authorizationService.authorizeRead(object, userId, role);
        return toFileAsset(object, true);
    }

    public String createInternalDownloadUrl(UUID objectId) {
        StorageObject object = requireReadyObject(objectId);
        return objectAccessUrl(object);
    }

    public StorageObjectInfo getInternalObjectInfo(UUID objectId) {
        return toStorageObjectInfo(requireObject(objectId));
    }

    public Map<UUID, String> createInternalDownloadUrls(List<UUID> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> urls = new HashMap<>();
        for (StorageObject object : storageObjectRepository.findByIdsAndStatus(objectIds, StorageObjectStatus.READY.name())) {
            urls.put(object.getId(), objectAccessUrl(object));
        }
        return urls;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(UUID objectId, UUID userId, Integer role) {
        StorageObject object = requireObject(objectId);
        authorizationService.authorizeDelete(object, userId, role);
        removeObjectIfExists(object);
        object.setDeleted(StorageObject.DELETED);
        object.setDeletedAt(Instant.now());
        storageObjectRepository.update(object);
    }

    private Map<String, String> createPostPolicy(String bucket, String objectKey, String contentType, long maxSizeBytes) {
        try {
            PostPolicy postPolicy = new PostPolicy(
                    bucket,
                    ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(storageProperties.getExpiry().getUploadMinutes())
            );
            postPolicy.addEqualsCondition("key", objectKey);
            postPolicy.addEqualsCondition("Content-Type", contentType);
            postPolicy.addContentLengthRangeCondition(1, maxSizeBytes);
            Map<String, String> formData = new LinkedHashMap<>(presignMinioClient.getPresignedPostFormData(postPolicy));
            formData.put("key", objectKey);
            formData.put("Content-Type", contentType);
            return formData;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "Failed to create upload ticket");
        }
    }

    private String presignedGetUrl(StorageObject object) {
        try {
            return presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.Http.Method.GET)
                    .bucket(object.getBucket())
                    .object(object.getObjectKey())
                    .expiry(storageProperties.getExpiry().getDownloadMinutes(), TimeUnit.MINUTES)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "Failed to create download URL");
        }
    }

    private String objectAccessUrl(StorageObject object) {
        return hasStablePublicUrl(object) ? publicObjectUrl(object) : presignedGetUrl(object);
    }

    private boolean hasStablePublicUrl(StorageObject object) {
        return StorageVisibility.PUBLIC_READ.name().equals(object.getVisibility())
                || StorageUsage.USER_AVATAR.name().equals(object.getUsage())
                || StorageUsage.COURSE_COVER.name().equals(object.getUsage());
    }

    private String publicObjectUrl(StorageObject object) {
        return stripTrailingSlash(externalEndpoint()) + "/" + object.getBucket() + "/" + object.getObjectKey();
    }

    private StorageObject requireObject(UUID objectId) {
        return storageObjectRepository.findById(objectId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.STORAGE_OBJECT_NOT_FOUND));
    }

    private StorageObject requireReadyObject(UUID objectId) {
        StorageObject object = requireObject(objectId);
        if (!StorageObjectStatus.READY.name().equals(object.getStatus())) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }
        return object;
    }

    private StorageUploadSession requirePendingSession(UUID objectId) {
        return uploadSessionRepository.findByObjectId(objectId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.BAD_REQUEST, "Upload session not found"));
    }

    private FileAsset toFileAsset(StorageObject object, boolean includeUrl) {
        return new FileAsset(
                object.getId(),
                object.getOriginalFilename(),
                object.getContentType(),
                object.getSizeBytes(),
                object.getUsage(),
                object.getVisibility(),
                includeUrl ? objectAccessUrl(object) : null,
                object.getUploadedAt()
        );
    }

    private StorageObjectInfo toStorageObjectInfo(StorageObject object) {
        return new StorageObjectInfo(
                object.getId(),
                object.getOriginalFilename(),
                object.getContentType(),
                object.getSizeBytes(),
                object.getUsage(),
                object.getVisibility(),
                object.getScopeType(),
                object.getScopeId(),
                object.getStatus(),
                object.getOwnerUserId(),
                object.getUploadedAt()
        );
    }

    private FilePolicy filePolicy(StorageUsage usage) {
        StorageProperties.Bucket bucket = storageProperties.getBucket();
        StorageProperties.Limits limits = storageProperties.getLimits();
        return switch (usage) {
            case USER_AVATAR -> new FilePolicy(bucket.getMedia(), StorageVisibility.PUBLIC_READ,
                    limits.getAvatarBytes(), IMAGE_TYPES);
            case COURSE_COVER -> new FilePolicy(bucket.getMedia(), StorageVisibility.PUBLIC_READ,
                    limits.getCourseCoverBytes(), IMAGE_TYPES);
            case FORUM_IMAGE -> new FilePolicy(bucket.getCourse(), StorageVisibility.COURSE_PRIVATE,
                    limits.getCourseCoverBytes(), IMAGE_TYPES);
            case COURSE_PUBLIC_FILE -> new FilePolicy(bucket.getCourse(), StorageVisibility.AUTHENTICATED,
                    limits.getCourseFileBytes(), COURSE_FILE_TYPES);
            case COURSE_PRIVATE_FILE -> new FilePolicy(bucket.getCourse(), StorageVisibility.COURSE_PRIVATE,
                    limits.getCourseFileBytes(), COURSE_FILE_TYPES);
            case AI_FILE -> new FilePolicy(bucket.getAi(), StorageVisibility.OWNER_PRIVATE,
                    limits.getAiFileBytes(), AI_FILE_TYPES);
        };
    }

    private UUID normalizeScopeId(CreateUploadRequest request, UUID userId) {
        if (request.scopeType() == StorageScopeType.USER && request.scopeId() == null) {
            return userId;
        }
        return request.scopeId();
    }

    private void validateContentType(String contentType, Set<String> allowedTypes) {
        if (!allowedTypes.contains(contentType)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Unsupported file type");
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private String safeFilename(String filename) {
        String normalized = filename == null ? "file" : filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        base = base.replaceAll("[\\r\\n\\t]", " ").trim();
        return base.isBlank() ? "file" : base;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String externalEndpoint() {
        String externalEndpoint = storageProperties.getMinio().getExternalEndpoint();
        return StringUtils.hasText(externalEndpoint) ? externalEndpoint : storageProperties.getMinio().getEndpoint();
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\"", "");
    }

    private void removeObjectIfExists(StorageObject object) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(object.getBucket())
                    .object(object.getObjectKey())
                    .build());
        } catch (ErrorResponseException exception) {
            if (!"NoSuchKey".equals(exception.errorResponse().code())) {
                throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "Failed to delete object");
            }
            // NoSuchKey: object already absent from MinIO, safe to ignore
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "Failed to delete object: " + exception.getMessage());
        }
    }

    private record FilePolicy(
            String bucket,
            StorageVisibility visibility,
            long maxSizeBytes,
            Set<String> allowedTypes
    ) {}
}
