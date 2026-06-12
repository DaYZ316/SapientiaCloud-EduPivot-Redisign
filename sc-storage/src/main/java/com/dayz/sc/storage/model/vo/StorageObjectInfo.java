package com.dayz.sc.storage.model.vo;

import java.time.Instant;
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
        Instant uploadedAt
) {}
