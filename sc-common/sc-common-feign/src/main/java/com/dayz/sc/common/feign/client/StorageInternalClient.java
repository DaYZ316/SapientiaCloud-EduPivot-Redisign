package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
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
    /**
     * 获取文件元数据信息。
     *
     * @param fileId 文件ID
     * @return 文件元数据信息
     */
    @GetMapping("/files/{fileId}")
    ApiResponse<@NonNull StorageObjectInfo> getFile(@PathVariable("fileId") UUID fileId);

    @GetMapping("/files/{fileId}/url")
    ApiResponse<String> getDownloadUrl(@PathVariable("fileId") UUID fileId);

    /**
     * 批量获取文件预签名访问URL。
     *
     * @param fileIds 文件ID列表
     * @return 文件ID到预签名URL的映射
     */
    @PostMapping("/files/urls")
    ApiResponse<@NonNull Map<@NonNull UUID, @NonNull String>> getUrls(@RequestBody List<UUID> fileIds);
}
