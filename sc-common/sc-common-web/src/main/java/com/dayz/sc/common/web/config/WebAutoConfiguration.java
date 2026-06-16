package com.dayz.sc.common.web.config;

import com.dayz.sc.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 注册 Servlet Web 层公共基础设施 Bean
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration
public class WebAutoConfiguration {
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
