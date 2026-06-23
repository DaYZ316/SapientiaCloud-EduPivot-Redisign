package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "sc-notification", path = "/api/notifications/internal/dashboard", fallback = NotificationDashboardInternalClientFallback.class)
public interface NotificationDashboardInternalClient {

    @GetMapping("/summary")
    ApiResponse<@NonNull DashboardNotificationSummary> getSummary(
            @RequestParam("userId") UUID userId,
            @RequestParam(value = "includeDistribution", defaultValue = "false") boolean includeDistribution);
}
