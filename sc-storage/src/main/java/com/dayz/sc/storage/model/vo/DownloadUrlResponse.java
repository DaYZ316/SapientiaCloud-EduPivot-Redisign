package com.dayz.sc.storage.model.vo;

import java.time.Instant;

public record DownloadUrlResponse(
        String url,
        Instant expiresAt
) {}
