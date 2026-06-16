package com.dayz.sc.auth.model.dto;

import com.dayz.sc.auth.model.enums.OauthProvider;

/**
 * 登录服务内部使用的通用 OAuth 用户信息
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record OauthUserInfo(
        OauthProvider provider,
        String providerUserId,
        String providerLogin,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl,
        String locale
) {
}
