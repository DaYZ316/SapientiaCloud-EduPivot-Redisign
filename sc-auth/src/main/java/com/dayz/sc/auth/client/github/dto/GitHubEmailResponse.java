package com.dayz.sc.auth.client.github.dto;

/**
 * GitHub emails 接口返回的邮箱信息
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record GitHubEmailResponse(
        String email,
        Boolean primary,
        Boolean verified,
        String visibility
) {
}
