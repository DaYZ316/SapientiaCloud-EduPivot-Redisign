package com.dayz.sc.auth.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.dayz.sc.common.persistence.type.UuidTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

/**
 * 注册认证模块配置属性。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties({GoogleOauthProperties.class, GitHubOauthProperties.class})
public class AuthAutoConfiguration {
    @Bean
    ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
        };
    }
}
