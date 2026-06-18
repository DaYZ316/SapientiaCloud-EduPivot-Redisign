package com.dayz.sc.auth.client.google;

import com.dayz.sc.auth.client.google.dto.GoogleTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用于通过 Google OAuth2 授权码换取访问令牌的 OpenFeign 客户端
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@FeignClient(name = "google-oauth-client", url = "https://oauth2.googleapis.com")
public interface GoogleOauthClient {
    /**
     * 使用授权码换取 Google OAuth2 访问令牌
     *
     * @param code         前端获取的 Google OAuth2 授权码
     * @param clientId     Google OAuth2 客户端编号
     * @param clientSecret Google OAuth2 客户端密钥
     * @param redirectUri  Google OAuth2 回调地址
     * @param grantType    授权类型，固定为 authorization_code
     * @return Google OAuth2 令牌响应
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    GoogleTokenResponse exchangeCode(
            @RequestParam("code") String code,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("grant_type") String grantType
    );
}
