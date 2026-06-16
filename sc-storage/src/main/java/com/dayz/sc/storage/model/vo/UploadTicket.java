package com.dayz.sc.storage.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * UploadTicket 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record UploadTicket(
        @JsonProperty("objectId") UUID objectId,
        @JsonProperty("uploadUrl") String uploadUrl,
        @JsonProperty("method") String method,
        @JsonProperty("formData") Map<String, String> formData,
        @JsonProperty("headers") Map<String, String> headers,
        @JsonProperty("expiresAt") Instant expiresAt,
        @JsonProperty("maxSizeBytes") long maxSizeBytes
) {}
