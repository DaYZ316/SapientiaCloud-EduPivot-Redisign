package com.dayz.sc.common.response;

import com.dayz.sc.common.error.ErrorCode;
import com.dayz.sc.common.error.ErrorCodes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * REST 接口统一响应体
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record ApiResponse<T>(
        int code,
        String message,
        @Nullable
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(@Nullable T data) {
        return new ApiResponse<>(ErrorCodes.SUCCESS.code(), ErrorCodes.SUCCESS.message(), data, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, Instant.now());
    }

    public static ApiResponse<Void> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }

    /**
     * 泛型失败响应，供 Feign Fallback 返回非 Void 类型时使用。
     */
    public static <T> ApiResponse<T> failOf(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null, Instant.now());
    }
}
