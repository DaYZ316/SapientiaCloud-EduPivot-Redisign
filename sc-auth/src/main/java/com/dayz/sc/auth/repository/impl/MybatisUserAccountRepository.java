package com.dayz.sc.auth.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.auth.mapper.UserIdentityMapper;
import com.dayz.sc.auth.mapper.UserMapper;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 PostgreSQL 和 Redis 缓存的用户账户仓储实现。
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>Cache-Aside 模式：读时加载，写时删除</li>
 *   <li>空值缓存：防穿透，不存在的数据缓存 2 分钟</li>
 *   <li>TTL 随机化：防雪崩，TTL = 基础值 + 随机偏移</li>
 *   <li>只删不更新：写操作删除缓存，下次读取时自动重建</li>
 * </ul>
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MybatisUserAccountRepository implements UserAccountRepository {

    /**
     * 基础缓存 TTL：30 分钟
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * TTL 随机偏移范围：0~5 分钟（防雪崩）
     */
    private static final Duration CACHE_JITTER = Duration.ofMinutes(5);

    /**
     * 空值缓存 TTL：2 分钟（防穿透）
     */
    private static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);

    /**
     * 空值标记字符串（序列化后仍可正确比较）
     */
    private static final String NULL_MARKER = "__NULL__";

    private static final String USER_KEY_PREFIX = "auth:user:";
    private static final String IDENTITY_KEY_PREFIX = "auth:identity:";
    private static final String PROVIDERS_KEY_SUFFIX = ":providers";
    private static final String EMAIL_KEY_PREFIX = "auth:user:email:";

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== 读操作 ====================

    @Override
    public Optional<UserIdentity> findIdentity(OauthProvider provider, String providerUserId) {
        String cacheKey = identityKey(provider, providerUserId);

        // 1. 查缓存
        Object cached = getFromCache(cacheKey);
        if (NULL_MARKER.equals(cached)) {
            return Optional.empty();
        }
        if (cached instanceof UserIdentity identity) {
            return Optional.of(identity);
        }

        // 2. 查 DB
        UserIdentity identity = userIdentityMapper.selectOne(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getProvider, provider)
                .eq(UserIdentity::getProviderUserId, providerUserId));

        // 3. 写缓存
        putToCache(cacheKey, identity);
        return Optional.ofNullable(identity);
    }

    @Override
    public Optional<User> findUser(UUID userId) {
        String cacheKey = userKey(userId);

        // 1. 查缓存
        Object cached = getFromCache(cacheKey);
        if (NULL_MARKER.equals(cached)) {
            return Optional.empty();
        }
        if (cached instanceof User user) {
            return Optional.of(user);
        }

        // 2. 查 DB
        User user = userMapper.selectById(userId);

        // 3. 写缓存
        putToCache(cacheKey, user);
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String cacheKey = emailKey(email);

        // 1. 查缓存
        Object cached = getFromCache(cacheKey);
        if (NULL_MARKER.equals(cached)) {
            return Optional.empty();
        }
        if (cached instanceof User user) {
            return Optional.of(user);
        }

        // 2. 查 DB
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));

        // 3. 写缓存
        putToCache(cacheKey, user);
        return Optional.ofNullable(user);
    }

    @Override
    public List<OauthProvider> findLinkedProviders(UUID userId) {
        String cacheKey = providersKey(userId);

        // 1. 查缓存
        Object cached = getFromCache(cacheKey);
        if (NULL_MARKER.equals(cached)) {
            return List.of();
        }
        if (cached instanceof List<?> list && !list.isEmpty()
                && list.getFirst() instanceof OauthProvider) {
            @SuppressWarnings("unchecked")
            List<OauthProvider> providers = (List<OauthProvider>) list;
            return providers;
        }

        // 2. 查 DB
        List<OauthProvider> providers = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                        .select(UserIdentity::getProvider)
                        .eq(UserIdentity::getUserId, userId))
                .stream()
                .map(UserIdentity::getProvider)
                .toList();

        // 3. 写缓存
        putToCache(cacheKey, providers.isEmpty() ? null : providers);
        return providers;
    }

    @Override
    public long countUsers(LambdaQueryWrapper<User> wrapper) {
        return userMapper.selectCount(wrapper);
    }

    @Override
    public List<User> findUsers(LambdaQueryWrapper<User> wrapper) {
        return userMapper.selectList(wrapper);
    }

    @Override
    public List<User> findUsersByIds(Collection<UUID> ids) {
        return userMapper.selectBatchIds(ids);
    }

    @Override
    public List<UserIdentity> findIdentities(LambdaQueryWrapper<UserIdentity> wrapper) {
        return userIdentityMapper.selectList(wrapper);
    }

    // ==================== 写操作（只删不更新） ====================

    @Override
    public User saveUser(User user) {
        int updated = userMapper.updateById(user);
        if (updated == 0) {
            userMapper.insert(user);
        }
        // 只删除缓存，不主动更新（下次读取时自动重建）
        evictUserCache(user.getId(), user.getEmail());
        return user;
    }

    @Override
    public UserIdentity saveIdentity(UserIdentity identity) {
        int updated = userIdentityMapper.updateById(identity);
        if (updated == 0) {
            userIdentityMapper.insert(identity);
        }
        // 删除相关缓存
        evictIdentityCache(identity);
        return identity;
    }

    // ==================== 缓存操作 ====================

    /**
     * 从缓存获取数据。
     *
     * @return 缓存值、NULL_MARKER（空值标记）、或 null（未命中）
     */
    private Object getFromCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Redis 读取失败，降级到 DB: key={}", key, e);
            return null;
        }
    }

    /**
     * 写入缓存。支持空值缓存（防穿透）和 TTL 随机化（防雪崩）。
     *
     * @param key   缓存 key
     * @param value 数据值（null 表示 DB 中不存在）
     */
    private void putToCache(String key, Object value) {
        try {
            if (value == null) {
                // 空值缓存：短 TTL，防穿透
                redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_CACHE_TTL);
            } else {
                // 正常缓存：TTL + 随机偏移，防雪崩
                Duration ttl = randomTtl();
                redisTemplate.opsForValue().set(key, value, ttl);
            }
        } catch (RuntimeException e) {
            log.warn("Redis 写入失败: key={}", key, e);
        }
    }

    /**
     * 删除缓存 key。
     */
    private void deleteCached(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis 删除失败: key={}", key, e);
        }
    }

    /**
     * 生成随机 TTL（防雪崩）。
     * TTL = 基础值 + [0, jitter) 随机偏移
     */
    private Duration randomTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, CACHE_JITTER.getSeconds());
        return CACHE_TTL.plusSeconds(jitterSeconds);
    }

    // ==================== 缓存失效 ====================

    /**
     * 清除用户相关缓存（写操作后调用）。
     */
    private void evictUserCache(UUID userId, String email) {
        deleteCached(userKey(userId));
        deleteCached(providersKey(userId));
        if (email != null && !email.isBlank()) {
            deleteCached(emailKey(email));
        }
    }

    /**
     * 清除 OAuth 身份相关缓存。
     */
    private void evictIdentityCache(UserIdentity identity) {
        deleteCached(identityKey(identity.getProvider(), identity.getProviderUserId()));
        deleteCached(providersKey(identity.getUserId()));
        // 身份关联的用户缓存也需要清除（providers 变了）
        deleteCached(userKey(identity.getUserId()));
    }

    // ==================== Key 生成 ====================

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String identityKey(OauthProvider provider, String providerUserId) {
        return IDENTITY_KEY_PREFIX + provider.name() + ":" + providerUserId;
    }

    private String providersKey(UUID userId) {
        return userKey(userId) + PROVIDERS_KEY_SUFFIX;
    }

    private String emailKey(String email) {
        return EMAIL_KEY_PREFIX + email;
    }
}
