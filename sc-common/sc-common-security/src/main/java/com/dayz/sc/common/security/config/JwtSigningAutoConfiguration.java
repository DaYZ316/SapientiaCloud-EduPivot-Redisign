package com.dayz.sc.common.security.config;

import com.dayz.sc.common.security.crypto.RsaKeyLoader;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

/**
 * JWT 签发自动配置。
 * <p>
 * 当配置了 {@code edupivot.security.jwt.private-key} 时使用 RS256 签发。
 * 同时提供 JwtDecoder，使 auth 服务也能验证自己签发的 token
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "edupivot.security.jwt", name = "private-key")
public class JwtSigningAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        try {
            RSAPrivateKey privateKey = RsaKeyLoader.loadPrivateKey(jwtProperties.getPrivateKey());
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    privateKey.getModulus(),
                    java.math.BigInteger.valueOf(65537)
            );
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID("sc-edupivot-rsa-key-1")
                    .build();

            JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
            return new NimbusJwtEncoder(jwkSource);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 RSA JwtEncoder", e);
        }
    }

    /**
     * auth 服务也需要 JwtDecoder 来验证自己签发的 token（用于 refresh/logout 端点）。
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoderFromPrivateKey(JwtProperties jwtProperties) {
        try {
            RSAPrivateKey privateKey = RsaKeyLoader.loadPrivateKey(jwtProperties.getPrivateKey());
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    privateKey.getModulus(),
                    java.math.BigInteger.valueOf(65537)
            );
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception e) {
            throw new IllegalStateException("无法从私钥创建 JwtDecoder", e);
        }
    }

    @Bean
    public com.dayz.sc.common.security.endpoint.JwksController jwksController(JwtProperties jwtProperties) {
        return new com.dayz.sc.common.security.endpoint.JwksController(jwtProperties);
    }
}
