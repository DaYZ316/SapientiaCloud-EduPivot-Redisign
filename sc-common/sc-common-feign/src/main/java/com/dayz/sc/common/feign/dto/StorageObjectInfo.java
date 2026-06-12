package com.dayz.sc.common.feign.dto;

import java.time.LocalDateTime;
import java.util.UUID;

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
        LocalDateTime uploadedAt
) {}
