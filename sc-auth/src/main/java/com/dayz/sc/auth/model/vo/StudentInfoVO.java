package com.dayz.sc.auth.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 学生专属信息。
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public record StudentInfoVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("studentNo") @Nullable
        String studentNo,
        @JsonProperty("grade") @Nullable
        String grade,
        @JsonProperty("major") @Nullable
        String major,
        @JsonProperty("school") @Nullable
        String school
) {
}
