package com.dayz.aeroverse.auth.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.aeroverse.auth.mapper.UserIdentityMapper;
import com.dayz.aeroverse.auth.mapper.UserMapper;
import com.dayz.aeroverse.auth.model.entity.User;
import com.dayz.aeroverse.auth.model.entity.UserIdentity;
import com.dayz.aeroverse.auth.model.enums.OauthProvider;
import com.dayz.aeroverse.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 PostgreSQL 和 Redis 缓存的用户账户仓储实现。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Repository
@RequiredArgsConstructor
public class MybatisUserAccountRepository implements UserAccountRepository {
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String USER_KEY_PREFIX = "auth:user:";
    private static final String IDENTITY_KEY_PREFIX = "auth:identity:";
    private static final String PROVIDERS_KEY_SUFFIX = ":providers";

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<UserIdentity> findIdentity(OauthProvider provider, String providerUserId) {
        String cacheKey = identityKey(provider, providerUserId);
        Optional<UserIdentity> cached = getCached(cacheKey, UserIdentity.class);
        if (cached.isPresent()) {
            return cached;
        }

        UserIdentity identity = userIdentityMapper.selectOne(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getProvider, provider)
                .eq(UserIdentity::getProviderUserId, providerUserId));
        putCached(cacheKey, identity);
        return Optional.ofNullable(identity);
    }

    @Override
    public Optional<User> findUser(UUID userId) {
        String cacheKey = userKey(userId);
        Optional<User> cached = getCached(cacheKey, User.class);
        if (cached.isPresent()) {
            return cached;
        }

        User user = userMapper.selectById(userId);
        putCached(cacheKey, user);
        return Optional.ofNullable(user);
    }

    @Override
    public User saveUser(User user) {
        int updated = userMapper.updateById(user);
        if (updated == 0) {
            userMapper.insert(user);
        }
        putCached(userKey(user.getId()), user);
        return user;
    }

    @Override
    public UserIdentity saveIdentity(UserIdentity identity) {
        int updated = userIdentityMapper.updateById(identity);
        if (updated == 0) {
            userIdentityMapper.insert(identity);
        }
        putCached(identityKey(identity.getProvider(), identity.getProviderUserId()), identity);
        deleteCached(providersKey(identity.getUserId()));
        return identity;
    }

    @Override
    public List<OauthProvider> findLinkedProviders(UUID userId) {
        String cacheKey = providersKey(userId);
        Optional<List> cached = getCached(cacheKey, List.class);
        if (cached.isPresent()) {
            @SuppressWarnings("unchecked")
            List<OauthProvider> providers = (List<OauthProvider>) cached.get();
            return providers;
        }

        List<OauthProvider> providers = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                        .select(UserIdentity::getProvider)
                        .eq(UserIdentity::getUserId, userId))
                .stream()
                .map(UserIdentity::getProvider)
                .toList();
        putCached(cacheKey, providers);
        return providers;
    }

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String identityKey(OauthProvider provider, String providerUserId) {
        return IDENTITY_KEY_PREFIX + provider.name() + ":" + providerUserId;
    }

    private String providersKey(UUID userId) {
        return userKey(userId) + PROVIDERS_KEY_SUFFIX;
    }

    private <T> Optional<T> getCached(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
        } catch (RuntimeException ignored) {
            // Redis only acts as a cache; fall back to database on failure.
        }
        return Optional.empty();
    }

    private void putCached(String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (RuntimeException ignored) {
            // Redis only acts as a cache; write failure does not block the flow.
        }
    }

    private void deleteCached(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ignored) {
            // Redis only acts as a cache; delete failure does not block the flow.
        }
    }
}
