package com.dayz.sc.course.model.dto;

import org.jspecify.annotations.Nullable;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record InvitationPageRequest(
        @Nullable Long page,
        @Nullable Long size,
        @Nullable Integer status
) {
}
