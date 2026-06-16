package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 携带 Google OAuth2 授权码的登录请求
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record GoogleLoginRequest(
        @NotBlank String code,
        String redirectUri
) {
}
