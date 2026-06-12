package com.dayz.sc.common.security.config;

import com.dayz.sc.common.security.service.JwtTokenService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全模块总自动配置。
 * <p>
 * 根据配置属性自动选择激活的子配置：
 * <ul>
 *   <li>有 privateKey → 激活 {@link JwtSigningAutoConfiguration}（auth 服务签发 JWT）</li>
 *   <li>有 jwksUri 或 publicKey → 激活 {@link JwtValidationAutoConfiguration}（其他服务验证 JWT）</li>
 *   <li>有 secret（向后兼容）→ 同时激活签名和验证（HS256 模式）</li>
 * </ul>
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@Import({JwtSigningAutoConfiguration.class, JwtValidationAutoConfiguration.class})
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
