package com.dayz.sc.common.feign.dto;

import java.util.UUID;

/**
 * InternalUserProfile.
 *
 * @author DaYZ
 */
public record InternalUserProfile(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        Integer role
) {
}
