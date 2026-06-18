package com.dayz.sc.common.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 签发与校验相关配置属性
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>RS256 非对称加密（推荐）：auth 服务使用 privateKey 签发，其他服务通过 jwksUri 或 publicKey 验证</li>
 *   <li>HS256 对称加密（向后兼容）：所有服务共享 secret</li>
 * </ul>
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "edupivot.security.jwt")
public class JwtProperties {

    /**
     * HS256 对称密钥（向后兼容，新服务应使用 RS256）
     */
    @Deprecated
    private String secret;

    /**
     * RS256 PEM 格式私钥，用于签发 JWT（auth 服务使用）
     */
    private String privateKey;

    /**
     * RS256 PEM 格式公钥，用于验证 JWT（可选，优先使用 jwksUri）
     */
    private String publicKey;

    /**
     * JWKS 端点地址，用于动态获取公钥（推荐用于微服务间）
     * 例：http://sc-auth:28081/.well-known/jwks.json
     */
    private String jwksUri;

    /**
     * Access Token 有效期
     */
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /**
     * Refresh Token 有效期
     */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /**
     * JWT 签发者
     */
    private String issuer = "sc-edupivot";

    /**
     * @deprecated 使用 {@link #getAccessTokenTtl()} 代替
     */
    @Deprecated
    public Duration getTtl() {
        return accessTokenTtl;
    }
}
