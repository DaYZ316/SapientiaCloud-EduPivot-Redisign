package com.dayz.sc.course.service;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves user basics for classroom surfaces with per-user cache entries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomUserInfoResolver {

    static final String CACHE_NAME = "userBasicInfo";

    private final AuthInternalClient authInternalClient;
    private final CacheManager cacheManager;

    public Map<UUID, UserBasicInfo> resolve(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctUserIds.isEmpty()) {
            return Map.of();
        }

        Cache cache = cacheManager.getCache(CACHE_NAME);
        Map<UUID, UserBasicInfo> resolved = new HashMap<>();
        List<UUID> missingUserIds = new ArrayList<>();

        for (UUID userId : distinctUserIds) {
            UserBasicInfo cached = cache != null ? cache.get(userId, UserBasicInfo.class) : null;
            if (cached != null) {
                resolved.put(userId, cached);
            } else {
                missingUserIds.add(userId);
            }
        }

        if (missingUserIds.isEmpty()) {
            return Map.copyOf(resolved);
        }

        Map<UUID, UserBasicInfo> loaded = loadFromAuth(missingUserIds);
        if (cache != null) {
            loaded.values().forEach(info -> cache.put(info.id(), info));
        }
        resolved.putAll(loaded);
        return Map.copyOf(resolved);
    }

    private Map<UUID, UserBasicInfo> loadFromAuth(List<UUID> userIds) {
        try {
            ApiResponse<@NonNull List<@NonNull UserBasicInfo>> response = authInternalClient.getUsersBasicInfo(userIds);
            if (response != null && response.code() == ErrorCodes.SUCCESS.code() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(UserBasicInfo::id, info -> info, (a, b) -> a));
            }
        } catch (Exception exception) {
            log.debug("Failed to resolve classroom user basic info", exception);
        }
        return Map.of();
    }
}
