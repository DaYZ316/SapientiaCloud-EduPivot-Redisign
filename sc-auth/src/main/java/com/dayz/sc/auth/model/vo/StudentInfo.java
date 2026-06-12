package com.dayz.sc.auth.model.vo;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 学生专属信息。
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public record StudentInfo(
        UUID id,
        @Nullable
        String studentNo,
        @Nullable
        String grade,
        @Nullable
        String major,
        @Nullable
        String school
) {
}
