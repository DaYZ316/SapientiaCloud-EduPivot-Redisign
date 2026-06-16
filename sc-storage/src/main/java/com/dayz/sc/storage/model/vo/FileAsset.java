package com.dayz.sc.storage.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * FileAsset 相关定义。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record FileAsset(
        @JsonProperty("id") UUID id,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("sizeBytes") long sizeBytes,
        @JsonProperty("usage") String usage,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("url") String url,
        @JsonProperty("uploadedAt") Instant uploadedAt
) {}
