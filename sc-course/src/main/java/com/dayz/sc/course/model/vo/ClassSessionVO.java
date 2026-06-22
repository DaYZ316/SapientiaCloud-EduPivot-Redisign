package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Class session response.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record ClassSessionVO(
        @JsonProperty UUID id,
        @JsonProperty UUID courseId,
        @JsonProperty UUID teacherId,
        @JsonProperty String title,
        @JsonProperty @Nullable String description,
        @JsonProperty Instant scheduledStartAt,
        @JsonProperty Instant scheduledEndAt,
        @JsonProperty @Nullable Instant publishedAt,
        @JsonProperty Integer roomSize,
        @JsonProperty String liveRoomName,
        @JsonProperty Integer liveStatus,
        @JsonProperty String liveStatusText,
        @JsonProperty @Nullable Instant liveStartedAt,
        @JsonProperty @Nullable Instant livePausedAt,
        @JsonProperty @Nullable Instant liveEndedAt,
        @JsonProperty Integer status,
        @JsonProperty String statusText,
        @JsonProperty boolean joined,
        @JsonProperty Instant createdAt,
        @JsonProperty Instant updatedAt
) {
}
