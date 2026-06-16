package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feign 客户端降级实现
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
public class StorageInternalClientFallback implements StorageInternalClient {

    @Override
    public ApiResponse<StorageObjectInfo> getFile(UUID fileId) {
        log.warn("StorageInternalClient fallback: getFile({})", fileId);
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Map<UUID, String>> getUrls(List<UUID> fileIds) {
        log.warn("StorageInternalClient fallback: getUrls({})", fileIds);
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }
}
