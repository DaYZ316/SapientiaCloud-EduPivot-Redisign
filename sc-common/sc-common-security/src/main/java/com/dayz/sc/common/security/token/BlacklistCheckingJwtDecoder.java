package com.dayz.sc.common.security.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 装饰器：在 JWT 验证后额外检查 token 是否在黑名单中。
 * <p>
 * 用于 Gateway 层拦截已登出但尚未过期的 token。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public class BlacklistCheckingJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;
    private final TokenBlacklistService tokenBlacklistService;

    public BlacklistCheckingJwtDecoder(JwtDecoder delegate, TokenBlacklistService tokenBlacklistService) {
        this.delegate = delegate;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate.decode(token);
        String jti = jwt.getId();
        if (jti != null && tokenBlacklistService.isBlacklisted(jti)) {
            throw new JwtException("Token 已被吊销");
        }
        return jwt;
    }
}
