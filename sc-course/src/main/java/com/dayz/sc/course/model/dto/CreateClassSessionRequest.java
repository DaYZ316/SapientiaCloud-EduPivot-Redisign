package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * 创建课堂会话草稿请求
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record CreateClassSessionRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull Instant scheduledStartAt,
        @NotNull Instant scheduledEndAt,
        @NotNull Integer roomSize
) {
}
