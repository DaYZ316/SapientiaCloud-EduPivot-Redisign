package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feign 客户端接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@FeignClient(name = "sc-storage", path = "/api/storage/internal", fallback = StorageInternalClientFallback.class)
public interface StorageInternalClient {
    @GetMapping("/files/{fileId}")
    ApiResponse<StorageObjectInfo> getFile(@PathVariable("fileId") UUID fileId);

    @PostMapping("/files/urls")
    ApiResponse<Map<UUID, String>> getUrls(@RequestBody List<UUID> fileIds);
}
