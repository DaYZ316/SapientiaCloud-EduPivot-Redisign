package com.dayz.sc.storage.model.dto;

import com.dayz.sc.storage.model.enums.StorageBucketType;
import com.dayz.sc.storage.model.enums.StorageScopeType;
import com.dayz.sc.storage.model.enums.StorageUsage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CreateUploadRequest(
        @NotNull StorageUsage usage,
        @NotNull StorageScopeType scopeType,
        UUID scopeId,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 128) String contentType,
        @Min(1) long sizeBytes,
        @Size(max = 128) String sha256,
        StorageBucketType bucketType
) {}
