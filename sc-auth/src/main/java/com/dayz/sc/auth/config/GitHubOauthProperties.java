package com.dayz.sc.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub OAuth 登录所需的客户端配置
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "edupivot.github.oauth2")
public class GitHubOauthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String apiVersion = "2022-11-28";
}
