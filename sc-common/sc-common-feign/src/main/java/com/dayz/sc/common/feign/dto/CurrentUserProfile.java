package com.dayz.sc.common.feign.dto;

/**
 * CurrentUserProfile.
 *
 * @author DaYZ
 */
public record CurrentUserProfile(
        String displayName,
        String accountName,
        String avatarUrl,
        String roleKey,
        String roleName
) {
}
