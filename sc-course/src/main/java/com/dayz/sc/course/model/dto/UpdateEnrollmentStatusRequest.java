package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record UpdateEnrollmentStatusRequest(
        @NotNull Integer status
) {
}
