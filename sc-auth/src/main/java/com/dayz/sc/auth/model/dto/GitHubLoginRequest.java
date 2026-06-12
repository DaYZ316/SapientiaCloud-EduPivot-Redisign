package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 携带 GitHub OAuth 授权码的登录请求。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record GitHubLoginRequest(
        @NotBlank String code,
        String redirectUri,
        String codeVerifier
) {
}
