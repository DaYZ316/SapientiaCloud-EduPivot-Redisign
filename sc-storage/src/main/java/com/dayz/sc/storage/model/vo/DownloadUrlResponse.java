package com.dayz.sc.storage.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record DownloadUrlResponse(
        @JsonProperty("url") String url,
        @JsonProperty("expiresAt") Instant expiresAt
) {}
