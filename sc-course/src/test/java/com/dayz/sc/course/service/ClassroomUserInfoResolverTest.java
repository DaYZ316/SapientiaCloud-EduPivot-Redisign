package com.dayz.sc.course.service;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomUserInfoResolverTest {

    @Mock
    private AuthInternalClient authInternalClient;

    private ConcurrentMapCacheManager cacheManager;
    private ClassroomUserInfoResolver resolver;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(ClassroomUserInfoResolver.CACHE_NAME);
        resolver = new ClassroomUserInfoResolver(authInternalClient, cacheManager);
    }

    @Test
    void resolve_shouldReturnCachedUserWithoutCallingAuth() {
        UUID userId = UUID.randomUUID();
        UserBasicInfo cached = new UserBasicInfo(userId, "Cached", "https://avatar.test/cached.png", 1);
        cache().put(userId, cached);

        var result = resolver.resolve(List.of(userId));

        assertThat(result).containsEntry(userId, cached);
        verifyNoInteractions(authInternalClient);
    }

    @Test
    void resolve_shouldRequestOnlyCacheMissesAndCacheLoadedUsers() {
        UUID cachedUserId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        UserBasicInfo cached = new UserBasicInfo(cachedUserId, "Cached", "https://avatar.test/cached.png", 1);
        UserBasicInfo loaded = new UserBasicInfo(missingUserId, "Loaded", "https://avatar.test/loaded.png", 2);
        cache().put(cachedUserId, cached);
        when(authInternalClient.getUsersBasicInfo(List.of(missingUserId))).thenReturn(ApiResponse.ok(List.of(loaded)));

        var result = resolver.resolve(List.of(cachedUserId, missingUserId, missingUserId));

        assertThat(result).containsEntry(cachedUserId, cached).containsEntry(missingUserId, loaded);
        assertThat(cache().get(missingUserId, UserBasicInfo.class)).isEqualTo(loaded);
        verify(authInternalClient).getUsersBasicInfo(List.of(missingUserId));
    }

    @Test
    void resolve_shouldReturnEmptyMap_whenAuthFallbackFails() {
        UUID userId = UUID.randomUUID();
        when(authInternalClient.getUsersBasicInfo(List.of(userId))).thenReturn(ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE));

        var result = resolver.resolve(List.of(userId));

        assertThat(result).isEmpty();
        assertThat(cache().get(userId)).isNull();
    }

    private Cache cache() {
        return cacheManager.getCache(ClassroomUserInfoResolver.CACHE_NAME);
    }
}
