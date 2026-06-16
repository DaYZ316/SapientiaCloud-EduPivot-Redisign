package com.dayz.sc.storage.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.storage.model.dto.CreateUploadRequest;
import com.dayz.sc.storage.model.vo.DownloadUrlResponse;
import com.dayz.sc.storage.model.vo.FileAsset;
import com.dayz.sc.storage.model.vo.StorageObjectInfo;
import com.dayz.sc.storage.model.vo.UploadTicket;
import com.dayz.sc.storage.service.DocConversionService;
import com.dayz.sc.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;
import java.util.Map;

/**
 * REST 控制器
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final DocConversionService docConversionService;

    @PostMapping("/uploads")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<UploadTicket> createUpload(@Valid @RequestBody CreateUploadRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(storageService.createUpload(request, userId, role));
    }

    @PostMapping("/uploads/{objectId}/complete")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<FileAsset> completeUpload(@PathVariable UUID objectId,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(storageService.completeUpload(objectId, userId, role));
    }

    @GetMapping("/files/{fileId}")
    public ApiResponse<FileAsset> getFile(@PathVariable UUID fileId,
                                          @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(storageService.getFile(fileId, userId, role));
    }

    @GetMapping("/files/{fileId}/download-url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@PathVariable UUID fileId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        return ApiResponse.ok(storageService.createDownloadUrl(fileId, userId, role));
    }

    @PostMapping("/files/{fileId}/convert")
    @RateLimited(maxRequests = 5, windowSeconds = 60)
    public ApiResponse<DownloadUrlResponse> convertFile(@PathVariable UUID fileId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        // 先校验用户对该文件的读权限
        storageService.getFile(fileId, userId, role);
        return ApiResponse.ok(docConversionService.convertDocToDocx(fileId));
    }

    @DeleteMapping("/files/{fileId}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deleteFile(@PathVariable UUID fileId,
                                        @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        storageService.deleteFile(fileId, userId, role);
        return ApiResponse.ok(null);
    }

    @GetMapping("/internal/files/{fileId}/url")
    public ApiResponse<String> internalDownloadUrl(@PathVariable UUID fileId) {
        return ApiResponse.ok(storageService.createInternalDownloadUrl(fileId));
    }

    @GetMapping("/internal/files/{fileId}")
    public ApiResponse<StorageObjectInfo> internalFile(@PathVariable UUID fileId) {
        return ApiResponse.ok(storageService.getInternalObjectInfo(fileId));
    }

    @PostMapping("/internal/files/urls")
    public ApiResponse<Map<UUID, String>> internalDownloadUrls(@RequestBody List<UUID> fileIds) {
        return ApiResponse.ok(storageService.createInternalDownloadUrls(fileIds));
    }
}
