package com.dayz.aeroverse.auth.client.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

/**
 * GitHub OAuth token 接口返回的令牌响应。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record GitHubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        String scope,
        @JsonProperty("token_type") String tokenType,
        String error,
        @JsonProperty("error_description") String errorDescription,
        @JsonProperty("error_uri") String errorUri
) {
    public boolean successful() {
        return StringUtils.hasText(accessToken) && !StringUtils.hasText(error);
    }
}
