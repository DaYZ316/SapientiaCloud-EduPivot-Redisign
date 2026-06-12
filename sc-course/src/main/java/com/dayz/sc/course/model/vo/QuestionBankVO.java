package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuestionBankVO(
        UUID id,
        UUID courseId,
        UUID sysUserId,
        String bankName,
        @Nullable String description,
        int bankType,
        @Nullable List<String> tags,
        int difficulty,
        long questionCount,
        Instant createdAt,
        Instant updatedAt
) {}
