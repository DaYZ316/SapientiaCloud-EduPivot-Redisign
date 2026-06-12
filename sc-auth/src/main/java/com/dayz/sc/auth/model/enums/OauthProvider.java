package com.dayz.sc.auth.model.enums;

/**
 * 智语·云枢支持的 OAuth 身份提供方。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public enum OauthProvider {
    /**
     * Google OAuth2 身份提供方。
     */
    GOOGLE,
    /**
     * GitHub OAuth 身份提供方。
     */
    GITHUB,
    /**
     * 账号密码登录（本地）。
     */
    LOCAL
}
