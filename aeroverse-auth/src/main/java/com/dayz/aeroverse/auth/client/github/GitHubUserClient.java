package com.dayz.aeroverse.auth.client.github;

import com.dayz.aeroverse.auth.client.github.dto.GitHubEmailResponse;
import com.dayz.aeroverse.auth.client.github.dto.GitHubUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用于读取 GitHub 用户资料的 OpenFeign 客户端。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@FeignClient(name = "github-user-client", url = "https://api.github.com")
public interface GitHubUserClient {
    /**
     * 使用 GitHub 访问令牌读取当前用户基础资料。
     *
     * @param authorization Bearer 访问令牌
     * @param accept GitHub REST API 响应格式
     * @param apiVersion GitHub REST API 版本
     * @return GitHub 用户基础资料
     */
    @GetMapping("/user")
    GitHubUserResponse getUser(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HttpHeaders.ACCEPT) String accept,
            @RequestHeader("X-GitHub-Api-Version") String apiVersion
    );

    /**
     * 使用 GitHub 访问令牌读取当前用户邮箱列表。
     *
     * @param authorization Bearer 访问令牌
     * @param accept GitHub REST API 响应格式
     * @param apiVersion GitHub REST API 版本
     * @param perPage 每页数量
     * @param page 页码
     * @return GitHub 用户邮箱列表
     */
    @GetMapping("/user/emails")
    List<GitHubEmailResponse> listEmails(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HttpHeaders.ACCEPT) String accept,
            @RequestHeader("X-GitHub-Api-Version") String apiVersion,
            @RequestParam("per_page") int perPage,
            @RequestParam("page") int page
    );
}
