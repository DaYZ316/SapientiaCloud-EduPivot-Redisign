package com.dayz.aeroverse.auth.model.vo;

import com.dayz.aeroverse.auth.model.enums.OauthProvider;
import com.dayz.aeroverse.auth.model.enums.UserStatus;

import java.util.List;
import java.util.UUID;

/**
 * 返回给客户端的安全用户资料字段。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record UserProfile(
        UUID id,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl,
        String locale,
        UserStatus status,
        OauthProvider createdProvider,
        String createdIp,
        OauthProvider lastLoginProvider,
        String lastLoginIp,
        long loginCount,
        List<OauthProvider> linkedProviders
) {
}
