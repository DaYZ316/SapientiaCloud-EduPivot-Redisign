package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * LiveKit access token response.
 */
public record LiveKitTokenVO(
        @JsonProperty String url,
        @JsonProperty String roomName,
        @JsonProperty String token,
        @JsonProperty Instant expiresAt
) {}
