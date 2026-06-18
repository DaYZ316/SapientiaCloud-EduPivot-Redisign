package com.dayz.sc.common.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HS256 共享密钥回退配置（向后兼容）
 * <p>
 * 当仅配置了 {@code edupivot.security.jwt.secret} 时激活，
 * 同时提供签名和验证能力，新服务应使用 RS256
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "edupivot.security.jwt", name = "secret")
public class Hs256FallbackSigningAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    @SuppressWarnings("deprecation")
    public JwtEncoder hs256JwtEncoder(JwtProperties jwtProperties) {
        SecretKeySpec secretKey = new SecretKeySpec(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

}
