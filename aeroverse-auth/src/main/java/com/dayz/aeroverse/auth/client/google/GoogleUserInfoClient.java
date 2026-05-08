package com.dayz.aeroverse.auth.client.google;

import com.dayz.aeroverse.auth.client.google.dto.GoogleUserInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用于读取 Google OpenID Connect 用户信息的 OpenFeign 客户端。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@FeignClient(name = "google-user-info-client", url = "https://openidconnect.googleapis.com")
public interface GoogleUserInfoClient {
    /**
     * 使用 Google 访问令牌读取用户基础资料。
     *
     * @param authorization Bearer 访问令牌
     * @return Google 用户信息响应
     */
    @GetMapping("/v1/userinfo")
    GoogleUserInfoResponse getUserInfo(@RequestHeader("Authorization") String authorization);
}
