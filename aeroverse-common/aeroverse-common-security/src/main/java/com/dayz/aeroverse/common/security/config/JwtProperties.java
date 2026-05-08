package com.dayz.aeroverse.common.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 签发与校验相关配置属性。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aeroverse.security.jwt")
public class JwtProperties {
    private String secret = "change-me-to-a-very-long-secret-with-at-least-32-characters";
    private Duration ttl = Duration.ofDays(7);
    private String issuer = "aeroverse-navigator";
}
