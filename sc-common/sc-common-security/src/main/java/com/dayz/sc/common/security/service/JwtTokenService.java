package com.dayz.sc.common.security.service;

import com.dayz.sc.common.security.config.JwtProperties;
import com.dayz.sc.common.util.UuidV7Generator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Map;

/**
 * 为已认证用户创建签名后的 JWT 访问令牌。
 * <p>
 * 自动根据密钥类型选择算法：有 privateKey 时使用 RS256，否则使用 HS256
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {

    /**
     * 创建 Access Token。
     *
     * @param subject JWT subject（通常是 userId）
     * @param claims  自定义声明
     * @return 签名后的 JWT 字符串
     */
    public String createAccessToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.getAccessTokenTtl()))
                .id(UuidV7Generator.generate().toString())
                .subject(subject);

        claims.forEach(builder::claim);

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader(), builder.build())).getTokenValue();
    }

    /**
     * 获取 Access Token TTL（秒）。
     */
    public long getAccessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtl().getSeconds();
    }

    private org.springframework.security.oauth2.jwt.JwsHeader jwsHeader() {
        boolean useRsa = jwtProperties.getPrivateKey() != null
                && !jwtProperties.getPrivateKey().isBlank();
        if (useRsa) {
            return org.springframework.security.oauth2.jwt.JwsHeader
                    .with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                    .build();
        }
        return org.springframework.security.oauth2.jwt.JwsHeader
                .with(MacAlgorithm.HS256)
                .build();
    }
}
