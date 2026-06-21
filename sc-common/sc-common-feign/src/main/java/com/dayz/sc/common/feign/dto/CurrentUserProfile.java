package com.dayz.sc.common.feign.dto;

public record CurrentUserProfile(
        String displayName,
        String accountName,
        String avatarUrl,
        String roleKey,
        String roleName
) {
}
