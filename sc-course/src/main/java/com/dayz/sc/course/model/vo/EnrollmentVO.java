package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 视图对象。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record EnrollmentVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("courseTitle") @Nullable String courseTitle,
        @JsonProperty("courseCoverUrl") @Nullable String courseCoverUrl,
        @JsonProperty("studentId") UUID studentId,
        @JsonProperty("studentName") @Nullable String studentName,
        @JsonProperty("status") int status,
        @JsonProperty("enrolledAt") Instant enrolledAt,
        @JsonProperty("completedAt") @Nullable Instant completedAt
) {}
