package com.dayz.sc.common.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册限流拦截器到 Spring MVC
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@RequiredArgsConstructor
public class RateLimitWebMvcConfigurer implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }
}
