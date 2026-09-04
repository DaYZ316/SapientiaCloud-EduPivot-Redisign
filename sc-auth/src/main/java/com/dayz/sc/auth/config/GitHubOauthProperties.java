package com.dayz.sc.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * GitHub OAuth App 中登记的精确回调地址。服务端仅接受此列表中的值，
     * 不允许客户端任意指定 redirect_uri。
     */
    private List<String> redirectUris = new ArrayList<>();
    private String apiVersion = "2022-11-28";
}
