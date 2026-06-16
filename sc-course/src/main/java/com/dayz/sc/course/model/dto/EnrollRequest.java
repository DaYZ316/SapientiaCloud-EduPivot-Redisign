package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record EnrollRequest(
        @NotNull UUID courseId
) {}
