package com.dayz.aeroverse.auth.model.vo;

/**
 * 通用登录响应。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfile user
) {
}
