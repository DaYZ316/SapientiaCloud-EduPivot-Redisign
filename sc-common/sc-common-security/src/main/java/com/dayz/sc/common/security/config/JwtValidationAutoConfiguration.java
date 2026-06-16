package com.dayz.sc.common.security.config;

import com.dayz.sc.common.security.crypto.RsaKeyLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT 验证自动配置。
 * <p>
 * 优先使用 JWKS URI 动态获取公钥（推荐），其次使用静态公钥。
 * 当两者都未配置时，尝试使用 HS256 共享密钥（向后兼容）
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public class JwtValidationAutoConfiguration {

    /**
     * 基于 JWKS URI 的 JwtDecoder（推荐用于微服务间）。
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "edupivot.security.jwt", name = "jwks-uri")
    public JwtDecoder jwksJwtDecoder(JwtProperties jwtProperties) {
        return NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwksUri()).build();
    }

    /**
     * 基于静态公钥的 JwtDecoder。
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "edupivot.security.jwt", name = "public-key")
    public JwtDecoder publicKeyJwtDecoder(JwtProperties jwtProperties) {
        RSAPublicKey publicKey = RsaKeyLoader.loadPublicKey(jwtProperties.getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    /**
     * HS256 共享密钥 JwtDecoder（向后兼容）。
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "edupivot.security.jwt", name = "secret")
    @SuppressWarnings("deprecation")
    public JwtDecoder hs256JwtDecoder(JwtProperties jwtProperties) {
        SecretKeySpec secretKey = new SecretKeySpec(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
    }
}
