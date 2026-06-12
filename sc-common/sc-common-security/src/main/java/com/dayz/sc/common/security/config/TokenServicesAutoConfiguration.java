package com.dayz.sc.common.security.config;

import com.dayz.sc.common.security.ratelimit.RateLimiterService;
import com.dayz.sc.common.security.ratelimit.RateLimitInterceptor;
import com.dayz.sc.common.security.ratelimit.RateLimitWebMvcConfigurer;
import com.dayz.sc.common.security.token.BlacklistCheckingJwtDecoder;
import com.dayz.sc.common.security.token.RefreshTokenService;
import com.dayz.sc.common.security.token.TokenBlacklistService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Token 管理服务自动配置。
 * <p>
 * 注册 RefreshTokenService、TokenBlacklistService、RateLimiterService 等 Bean。
 * 这些 Bean 通过自动配置注册，而非 @Service，以确保在任何服务的组件扫描范围内都能被发现。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
public class TokenServicesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RefreshTokenService refreshTokenService(RedisTemplate<String, Object> redisTemplate, JwtProperties jwtProperties) {
        return new RefreshTokenService(redisTemplate, jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistService tokenBlacklistService(RedisTemplate<String, Object> redisTemplate) {
        return new TokenBlacklistService(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterService rateLimiterService(org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        return new RateLimiterService(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public RateLimitInterceptor rateLimitInterceptor(RateLimiterService rateLimiterService) {
        return new RateLimitInterceptor(rateLimiterService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public RateLimitWebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitInterceptor rateLimitInterceptor) {
        return new RateLimitWebMvcConfigurer(rateLimitInterceptor);
    }
}
