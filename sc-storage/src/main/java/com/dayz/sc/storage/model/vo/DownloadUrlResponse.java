package com.dayz.sc.storage.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * 响应 DTO。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record DownloadUrlResponse(
        @JsonProperty("url") String url,
        @JsonProperty("expiresAt") Instant expiresAt
) {}
