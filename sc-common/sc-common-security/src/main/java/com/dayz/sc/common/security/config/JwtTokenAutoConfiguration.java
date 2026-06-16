package com.dayz.sc.common.security.config;

import com.dayz.sc.common.security.service.JwtTokenService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * JWT token 服务自动配置。
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@AutoConfiguration(after = {
        JwtSigningAutoConfiguration.class,
        Hs256FallbackSigningAutoConfiguration.class
})
public class JwtTokenAutoConfiguration {

    @Bean
    @ConditionalOnBean(JwtEncoder.class)
    @ConditionalOnMissingBean(JwtTokenService.class)
    public JwtTokenService jwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        return new JwtTokenService(jwtEncoder, jwtProperties);
    }
}
