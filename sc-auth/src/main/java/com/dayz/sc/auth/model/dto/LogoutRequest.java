package com.dayz.sc.auth.model.dto;

import org.jspecify.annotations.Nullable;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record LogoutRequest(
        @Nullable String refreshToken
) {
}
