package com.dayz.aeroverse.common.feign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 启用灵境航途基础包下的 OpenFeign 客户端。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration
@EnableFeignClients(basePackages = "com.dayz.aeroverse")
public class FeignAutoConfiguration {
}
