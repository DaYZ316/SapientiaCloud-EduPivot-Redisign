package com.dayz.sc.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth2 登录所需的客户端配置
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "edupivot.google.oauth2")
public class GoogleOauthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
}
