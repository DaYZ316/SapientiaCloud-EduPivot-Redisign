package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Auth 服务降级处理
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@Slf4j
@Component
public class AuthInternalClientFallback implements FallbackFactory<AuthInternalClient> {

    @Override
    public AuthInternalClient create(Throwable cause) {
        log.warn("Auth service fallback triggered: {}", cause.getMessage());
        return ids -> ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }
}
