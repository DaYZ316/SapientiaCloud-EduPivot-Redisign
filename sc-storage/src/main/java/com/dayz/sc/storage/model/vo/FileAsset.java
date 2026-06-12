package com.dayz.sc.storage.model.vo;

import java.time.Instant;
import java.util.UUID;

public record FileAsset(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        String usage,
        String visibility,
        String url,
        Instant uploadedAt
) {}
