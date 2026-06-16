package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record BindCourseFileRequest(
        @NotNull UUID fileId,
        @Size(max = 255) String displayName,
        Integer sortOrder
) {}
