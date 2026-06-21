package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.feign.dto.InternalUserProfile;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Auth 服务内部调用客户端
 * <p>
 * 用于服务间通信，获取用户基本信息
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@FeignClient(name = "sc-auth", path = "/api/auth/users", fallbackFactory = AuthInternalClientFallback.class)
public interface AuthInternalClient {

    /**
     * 批量获取用户基本信息（displayName, avatarUrl）
     *
     * @param ids 用户 ID 列表
     * @return 用户基本信息列表
     */
    @GetMapping("/internal/basic")
    ApiResponse<@NonNull List<@NonNull UserBasicInfo>> getUsersBasicInfo(@RequestParam("ids") List<UUID> ids);

    @GetMapping("/internal/profile")
    ApiResponse<@NonNull InternalUserProfile> getUserProfile(@RequestParam("id") UUID id);

    @GetMapping("/me")
    ApiResponse<@NonNull InternalUserProfile> getCurrentUserProfile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
