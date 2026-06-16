package com.dayz.sc.storage.model.vo;

import java.time.Instant;
import java.util.UUID;

/**
 * 信息 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record StorageObjectInfo(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        String usage,
        String visibility,
        String scopeType,
        UUID scopeId,
        String status,
        UUID ownerUserId,
        Instant uploadedAt
) {}
