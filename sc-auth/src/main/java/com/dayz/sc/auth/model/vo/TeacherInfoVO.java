package com.dayz.sc.auth.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 教师专属信息。
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public record TeacherInfoVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("employeeNo") @Nullable
        String employeeNo,
        @JsonProperty("department") @Nullable
        String department,
        @JsonProperty("title") @Nullable
        String title,
        @JsonProperty("school") @Nullable
        String school
) {
}
