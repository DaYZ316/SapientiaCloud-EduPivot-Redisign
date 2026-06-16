package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 视图对象
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record QuestionBankVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("sysUserId") UUID sysUserId,
        @JsonProperty("bankName") String bankName,
        @JsonProperty("description") @Nullable String description,
        @JsonProperty("bankType") int bankType,
        @JsonProperty("tags") @Nullable List<String> tags,
        @JsonProperty("difficulty") int difficulty,
        @JsonProperty("questionCount") long questionCount,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {
}
