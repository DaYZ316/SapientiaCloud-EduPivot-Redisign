package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationDashboardInternalClientFallback implements NotificationDashboardInternalClient {

    @Override
    public ApiResponse<@NonNull DashboardNotificationSummary> getSummary(UUID userId, boolean includeDistribution) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }
}
