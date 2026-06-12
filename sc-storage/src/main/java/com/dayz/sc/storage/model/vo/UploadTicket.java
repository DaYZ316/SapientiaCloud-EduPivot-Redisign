package com.dayz.sc.storage.model.vo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UploadTicket(
        UUID objectId,
        String uploadUrl,
        String method,
        Map<String, String> formData,
        Map<String, String> headers,
        Instant expiresAt,
        long maxSizeBytes
) {}
