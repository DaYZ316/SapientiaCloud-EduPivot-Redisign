package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 请求 DTO。
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
