package com.dayz.sc.gateway.config;

import com.dayz.sc.common.security.config.JwtProperties;
import com.dayz.sc.common.security.token.BlacklistCheckingJwtDecoder;
import com.dayz.sc.common.security.token.TokenBlacklistService;
import com.dayz.sc.gateway.filter.ProfileCompletionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Gateway 安全配置
 * <p>
 * 作为系统第一道防线，验证 JWT 并检查 Token 黑名单
 * 从 sc-auth 的 JWKS 端点获取公钥进行 RS256 验证
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Configuration
public class GatewaySecurityConfiguration {

    private static final String NOTIFICATION_SUBSCRIBE_ENDPOINT = "/api/notifications/subscribe";

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/google/login",
            "/api/auth/github/login",
            "/api/auth/register",
            "/api/auth/password/login",
            "/api/auth/refresh",
            "/api/ai/live-summaries/class-sessions/*/audio",
            "/api/class-sessions/seats/ws",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
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
            "/openapi/**",
            "/error"
    };

    @Bean
    public SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                          BearerTokenResolver bearerTokenResolver) throws Exception {
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
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .addFilterAfter(new ProfileCompletionFilter(new ObjectMapper()), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> {
            if (isNotificationSubscribeRequest(request)) {
                String token = request.getParameter("token");
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
            return defaultResolver.resolve(request);
        };
    }

    private boolean isNotificationSubscribeRequest(HttpServletRequest request) {
        return request.getRequestURI().equals(request.getContextPath() + NOTIFICATION_SUBSCRIBE_ENDPOINT);
    }

    /**
     * 带黑名单检查的 JwtDecoder
     * <p>
     * 优先使用 JWKS URI，回退到 HS256
     */
    @Bean
    @SuppressWarnings("deprecation")
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties, TokenBlacklistService tokenBlacklistService) {
        JwtDecoder delegate;
        if (jwtProperties.getJwksUri() != null && !jwtProperties.getJwksUri().isBlank()) {
            delegate = NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwksUri()).build();
        } else if (jwtProperties.getSecret() != null && !jwtProperties.getSecret().isBlank()) {
            var secretKey = new javax.crypto.spec.SecretKeySpec(
                    jwtProperties.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            delegate = NimbusJwtDecoder.withSecretKey(secretKey)
                    .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                    .build();
        } else {
            throw new IllegalStateException("Gateway 需要配置 edupivot.security.jwt.jwks-uri 或 edupivot.security.jwt.secret");
        }
        return new BlacklistCheckingJwtDecoder(delegate, tokenBlacklistService);
    }
}
