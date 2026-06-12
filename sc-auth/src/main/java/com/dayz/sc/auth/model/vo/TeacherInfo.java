package com.dayz.sc.auth.model.vo;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 教师专属信息。
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public record TeacherInfo(
        UUID id,
        @Nullable
        String employeeNo,
        @Nullable
        String department,
        @Nullable
        String title,
        @Nullable
        String school
) {
}
