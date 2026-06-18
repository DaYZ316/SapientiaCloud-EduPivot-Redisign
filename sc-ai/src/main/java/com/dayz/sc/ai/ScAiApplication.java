package com.dayz.sc.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * sc-ai 服务启动类（AI 教学助手 Celestial Hub）。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ScAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScAiApplication.class, args);
    }
}
