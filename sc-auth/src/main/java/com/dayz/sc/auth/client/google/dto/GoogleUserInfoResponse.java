package com.dayz.sc.auth.client.google.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google OpenID Connect userinfo 接口返回的用户信息
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record GoogleUserInfoResponse(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name,
        String picture,
        String locale
) {
}
