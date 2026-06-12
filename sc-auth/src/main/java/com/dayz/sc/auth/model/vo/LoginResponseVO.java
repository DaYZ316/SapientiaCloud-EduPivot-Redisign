package com.dayz.sc.auth.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * 通用登录响应。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record LoginResponseVO(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("tokenType") String tokenType,
        @JsonProperty("expiresIn") Long expiresIn,
        @JsonProperty("user") @Nullable UserProfileVO user
) {
}
