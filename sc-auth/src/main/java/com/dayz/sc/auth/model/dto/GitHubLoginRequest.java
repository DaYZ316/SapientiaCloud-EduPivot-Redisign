package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 携带 GitHub OAuth 授权码的登录请求
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record GitHubLoginRequest(
        @NotBlank @Size(max = 1024) String code,
        @NotBlank @Size(max = 2048) String redirectUri,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9\\-._~]{43,128}$", message = "codeVerifier 格式无效")
        String codeVerifier
) {
}
