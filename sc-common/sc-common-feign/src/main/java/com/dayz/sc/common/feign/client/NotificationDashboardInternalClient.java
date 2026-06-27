package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Notification 仪表盘内部调用客户端
 *
 * @author DaYZ
 * @since 2026-06-27
 */
@FeignClient(name = "sc-notification", path = "/api/notifications/internal/dashboard", fallback = NotificationDashboardInternalClientFallback.class)
public interface NotificationDashboardInternalClient {

    /**
     * 获取通知仪表盘摘要
     *
     * @param userId              用户ID
     * @param includeDistribution 是否包含分布统计
     * @return 通知摘要信息
     */
    @GetMapping("/summary")
    ApiResponse<@NonNull DashboardNotificationSummary> getSummary(
            @RequestParam("userId") UUID userId,
            @RequestParam(value = "includeDistribution", defaultValue = "false") boolean includeDistribution);
}
