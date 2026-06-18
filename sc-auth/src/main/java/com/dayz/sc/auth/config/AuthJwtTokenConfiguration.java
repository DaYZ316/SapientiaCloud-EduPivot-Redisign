package com.dayz.sc.auth.config;

import com.dayz.sc.common.security.config.JwtProperties;
import com.dayz.sc.common.security.service.JwtTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * Auth 服务 JWT token 配置
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@Configuration(proxyBeanMethods = false)
public class AuthJwtTokenConfiguration {

    @Bean
    @ConditionalOnBean(JwtEncoder.class)
    @ConditionalOnMissingBean(JwtTokenService.class)
    public JwtTokenService jwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        return new JwtTokenService(jwtEncoder, jwtProperties);
    }
}
