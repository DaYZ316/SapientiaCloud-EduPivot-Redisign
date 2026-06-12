package com.dayz.sc.auth.model.vo;

/**
 * 通用登录响应。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserProfile user
) {
}
