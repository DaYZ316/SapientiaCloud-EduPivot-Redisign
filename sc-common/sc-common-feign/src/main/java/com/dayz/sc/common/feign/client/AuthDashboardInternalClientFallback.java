package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.dashboard.DashboardUserSummary;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * AuthDashboardInternalClientFallback.
 *
 * @author DaYZ
 */
@Component
public class AuthDashboardInternalClientFallback implements AuthDashboardInternalClient {

    @Override
    public ApiResponse<@NonNull DashboardUserSummary> getUserSummary() {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }
}
