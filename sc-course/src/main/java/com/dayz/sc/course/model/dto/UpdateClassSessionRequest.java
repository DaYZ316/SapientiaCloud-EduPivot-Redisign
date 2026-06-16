package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 更新课堂会话草稿请求
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record UpdateClassSessionRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Integer roomSize
) {
}
