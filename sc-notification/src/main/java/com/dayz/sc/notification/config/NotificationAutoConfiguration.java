package com.dayz.sc.notification.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.dayz.sc.common.persistence.type.UuidTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

/**
 * 通知服务 MyBatis 自动配置。
 * <p>
 * 注册 PostgreSQL UUID 类型处理器
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
public class NotificationAutoConfiguration {

    @Bean
    ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
        };
    }
}
