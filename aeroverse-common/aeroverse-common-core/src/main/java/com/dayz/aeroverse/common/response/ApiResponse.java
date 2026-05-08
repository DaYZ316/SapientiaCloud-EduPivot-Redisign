package com.dayz.aeroverse.common.response;

import com.dayz.aeroverse.common.error.ErrorCode;
import com.dayz.aeroverse.common.error.ErrorCodes;

import java.time.Instant;

/**
 * REST 接口统一响应体。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
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
}
