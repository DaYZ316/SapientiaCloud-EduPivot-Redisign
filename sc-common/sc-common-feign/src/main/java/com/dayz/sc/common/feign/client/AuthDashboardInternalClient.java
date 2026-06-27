package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.dashboard.DashboardUserSummary;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Auth 仪表盘内部调用客户端
 *
 * @author DaYZ
 * @since 2026-06-27
 */
@FeignClient(
        contextId = "authDashboardInternalClient",
        name = "sc-auth",
        path = "/api/auth/users/internal/dashboard",
        fallback = AuthDashboardInternalClientFallback.class)
public interface AuthDashboardInternalClient {

    /**
     * 获取用户仪表盘摘要
     *
     * @return 用户摘要信息
     */
    @GetMapping("/summary")
    ApiResponse<@NonNull DashboardUserSummary> getUserSummary();
}
