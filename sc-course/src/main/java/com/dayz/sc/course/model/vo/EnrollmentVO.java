package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentVO(
        UUID id,
        UUID courseId,
        @Nullable String courseTitle,
        @Nullable String courseCoverUrl,
        UUID studentId,
        @Nullable String studentName,
        int status,
        Instant enrolledAt,
        @Nullable Instant completedAt
) {}
