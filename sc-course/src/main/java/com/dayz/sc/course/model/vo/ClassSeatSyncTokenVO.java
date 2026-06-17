package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket seat sync token response.
 *
 * @author DaYZ
 * @since 2026-06-17
 */
public record ClassSeatSyncTokenVO(
        @JsonProperty String token,
        @JsonProperty long expiresInSeconds
) {
}
