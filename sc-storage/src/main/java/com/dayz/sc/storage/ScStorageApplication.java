package com.dayz.sc.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 存储服务启动类
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ScStorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScStorageApplication.class, args);
    }
}
