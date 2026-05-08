package com.dayz.aeroverse.common.security.config;

import com.dayz.aeroverse.common.security.service.JwtTokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 配置基于 Bearer JWT 的无状态接口安全机制。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfiguration {
    private static final String JWS_ALGORITHM = "HmacSHA256";
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/google/login",
            "/api/auth/github/login",
            "/actuator/health",
            "/doc.html",
            "/scalar",
            "/scalar.js",
            "/scalar/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {
                }));

        return http.build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        SecretKeySpec secretKey = new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), JWS_ALGORITHM);
        ImmutableSecret<SecurityContext> secret = new ImmutableSecret<>(secretKey);
        return new NimbusJwtEncoder(secret);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        SecretKeySpec secretKey = new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), JWS_ALGORITHM);
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtTokenService jwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        return new JwtTokenService(jwtEncoder, jwtProperties);
    }
}
