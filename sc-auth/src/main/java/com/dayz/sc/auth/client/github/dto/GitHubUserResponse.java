package com.dayz.sc.auth.client.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub 当前用户接口返回的用户资料。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record GitHubUserResponse(
        Long id,
        String login,
        String name,
        String email,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
