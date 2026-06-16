package com.dayz.sc.auth.client.google.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

/**
 * Google OAuth2 token 接口返回的令牌响应
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("id_token") String idToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
    public boolean successful() {
        return StringUtils.hasText(accessToken) && !StringUtils.hasText(error);
    }
}
