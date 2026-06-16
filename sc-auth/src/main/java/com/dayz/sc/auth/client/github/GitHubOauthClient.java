package com.dayz.sc.auth.client.github;

import com.dayz.sc.auth.client.github.dto.GitHubTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用于通过 GitHub OAuth 授权码换取访问令牌的 OpenFeign 客户端
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@FeignClient(name = "github-oauth-client", url = "https://github.com")
public interface GitHubOauthClient {
    /**
     * 使用授权码换取 GitHub OAuth 访问令牌。
     *
     * @param accept       响应格式，固定请求 JSON
     * @param clientId     GitHub OAuth App 客户端编号
     * @param clientSecret GitHub OAuth App 客户端密钥
     * @param code         前端获取的 GitHub OAuth 授权码
     * @param redirectUri  GitHub OAuth 回调地址
     * @param codeVerifier PKCE 校验码，可为空
     * @return GitHub OAuth 令牌响应
     */
    @PostMapping(value = "/login/oauth/access_token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    GitHubTokenResponse exchangeCode(
            @RequestHeader(HttpHeaders.ACCEPT) String accept,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier
    );
}
