package com.dayz.sc.common.feign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * 信息 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record StorageObjectInfo(
        @JsonProperty("id") UUID id,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("sizeBytes") long sizeBytes,
        @JsonProperty("usage") String usage,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("scopeType") String scopeType,
        @JsonProperty("scopeId") UUID scopeId,
        @JsonProperty("status") String status,
        @JsonProperty("ownerUserId") UUID ownerUserId,
        @JsonProperty("uploadedAt") Instant uploadedAt
) {
}
