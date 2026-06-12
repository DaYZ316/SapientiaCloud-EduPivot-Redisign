package com.dayz.sc.storage.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

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
