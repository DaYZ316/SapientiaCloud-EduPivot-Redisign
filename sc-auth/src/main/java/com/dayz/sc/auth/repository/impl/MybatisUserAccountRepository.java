package com.dayz.sc.auth.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.auth.mapper.UserIdentityMapper;
import com.dayz.sc.auth.mapper.UserMapper;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.repository.UserAccountRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 基于 PostgreSQL 和 Redis 缓存的用户账户仓储实现。
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>Cache-Aside 模式：读时加载，写时删除</li>
 *   <li>空值缓存：不存在的数据缓存 2 分钟，降低缓存穿透风险</li>
 *   <li>TTL 随机化：基础 TTL 加随机偏移，降低缓存雪崩风险</li>
 *   <li>互斥重建：缓存未命中时使用 Redis 锁，降低热点 key 击穿风险</li>
 * </ul>
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Slf4j
@Repository
public class MybatisUserAccountRepository implements UserAccountRepository {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration CACHE_JITTER = Duration.ofMinutes(5);
    private static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);

    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final Duration LOCK_RETRY_SLEEP = Duration.ofMillis(50);
    private static final int MAX_LOCK_RETRIES = 3;

    private static final String NULL_MARKER = "__NULL__";
    private static final String USER_KEY_PREFIX = "auth:user:";
    private static final String IDENTITY_KEY_PREFIX = "auth:identity:";
    private static final String PROVIDERS_KEY_SUFFIX = ":providers";
    private static final String EMAIL_KEY_PREFIX = "auth:user:email:";
    private static final String LOCK_KEY_PREFIX = "lock:";

    private static final String CACHE_USER = "user";
    private static final String CACHE_EMAIL = "email";
    private static final String CACHE_IDENTITY = "identity";
    private static final String CACHE_PROVIDERS = "providers";

    private static final String METRIC_READ = "redis.auth.cache.read";
    private static final String METRIC_REBUILD = "redis.auth.cache.rebuild";
    private static final String METRIC_LOCK = "redis.auth.cache.lock";
    private static final String METRIC_EVICT = "redis.auth.cache.evict";

    private static final String RESULT_HIT = "hit";
    private static final String RESULT_NEGATIVE_HIT = "negative_hit";
    private static final String RESULT_MISS = "miss";
    private static final String RESULT_ERROR = "error";
    private static final String RESULT_DB_HIT = "db_hit";
    private static final String RESULT_DB_MISS = "db_miss";
    private static final String RESULT_ACQUIRED = "acquired";
    private static final String RESULT_BUSY = "busy";
    private static final String RESULT_RELEASED = "released";
    private static final String RESULT_RELEASE_ERROR = "release_error";
    private static final String RESULT_TIMEOUT = "timeout";
    private static final String RESULT_SUCCESS = "success";

    private static final String RELEASE_LOCK_SCRIPT_TEXT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;
    private static final DefaultRedisScript<@NonNull Long> RELEASE_LOCK_SCRIPT = releaseLockScript();

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final RedisTemplate<@NonNull String, @NonNull Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<@NonNull MeterRegistry> meterRegistryProvider;

    public MybatisUserAccountRepository(UserMapper userMapper,
                                        UserIdentityMapper userIdentityMapper,
                                        RedisTemplate<@NonNull String, @NonNull Object> redisTemplate,
                                        StringRedisTemplate stringRedisTemplate,
                                        ObjectProvider<@NonNull MeterRegistry> meterRegistryProvider) {
        this.userMapper = userMapper;
        this.userIdentityMapper = userIdentityMapper;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    private static DefaultRedisScript<@NonNull Long> releaseLockScript() {
        DefaultRedisScript<@NonNull Long> script = new DefaultRedisScript<>();
        script.setScriptText(RELEASE_LOCK_SCRIPT_TEXT);
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public Optional<UserIdentity> findIdentity(OauthProvider provider, String providerUserId) {
        String cacheKey = identityKey(provider, providerUserId);
        UserIdentity identity = loadThroughCache(cacheKey, CACHE_IDENTITY,
                () -> readObjectCache(cacheKey, UserIdentity.class, CACHE_IDENTITY),
                () -> userIdentityMapper.selectOne(new LambdaQueryWrapper<UserIdentity>()
                        .eq(UserIdentity::getProvider, provider)
                        .eq(UserIdentity::getProviderUserId, providerUserId)),
                value -> putToCache(cacheKey, value, CACHE_IDENTITY));
        return Optional.ofNullable(identity);
    }

    @Override
    public Optional<User> findUser(UUID userId) {
        String cacheKey = userKey(userId);
        User user = loadThroughCache(cacheKey, CACHE_USER,
                () -> readObjectCache(cacheKey, User.class, CACHE_USER),
                () -> userMapper.selectById(userId),
                value -> putToCache(cacheKey, value, CACHE_USER));
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String cacheKey = emailKey(email);
        User user = loadThroughCache(cacheKey, CACHE_EMAIL,
                () -> readObjectCache(cacheKey, User.class, CACHE_EMAIL),
                () -> userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email)),
                value -> putToCache(cacheKey, value, CACHE_EMAIL));
        return Optional.ofNullable(user);
    }

    @Override
    public List<OauthProvider> findLinkedProviders(UUID userId) {
        String cacheKey = providersKey(userId);
        return loadThroughCache(cacheKey, CACHE_PROVIDERS,
                () -> readProvidersCache(cacheKey),
                () -> userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                                .select(UserIdentity::getProvider)
                                .eq(UserIdentity::getUserId, userId))
                        .stream()
                        .map(UserIdentity::getProvider)
                        .toList(),
                value -> putProvidersToCache(cacheKey, value));
    }

    @Override
    public long countUsers(LambdaQueryWrapper<User> wrapper) {
        return userMapper.selectCount(wrapper);
    }

    @Override
    public List<User> findUsers(LambdaQueryWrapper<User> wrapper) {
        return userMapper.selectList(wrapper);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<User> findUsersByIds(Collection<UUID> ids) {
        return userMapper.selectBatchIds(ids);
    }

    @Override
    public List<UserIdentity> findIdentities(LambdaQueryWrapper<UserIdentity> wrapper) {
        return userIdentityMapper.selectList(wrapper);
    }

    @Override
    public User saveUser(User user) {
        String previousEmail = null;
        if (user.getId() != null) {
            User previous = userMapper.selectById(user.getId());
            previousEmail = previous == null ? null : previous.getEmail();
        }
        int updated = userMapper.updateById(user);
        if (updated == 0) {
            userMapper.insert(user);
        }
        evictUserCache(user.getId(), previousEmail, user.getEmail());
        return user;
    }

    @Override
    public UserIdentity saveIdentity(UserIdentity identity) {
        int updated = userIdentityMapper.updateById(identity);
        if (updated == 0) {
            userIdentityMapper.insert(identity);
        }
        evictIdentityCache(identity);
        return identity;
    }

    private <T> T loadThroughCache(String cacheKey,
                                   String cacheName,
                                   Supplier<CacheLookupResult<T>> cacheReader,
                                   Supplier<T> dbLoader,
                                   Consumer<T> cacheWriter) {
        CacheLookupResult<T> cached = cacheReader.get();
        if (cached.hit()) {
            return cached.value();
        }
        if (cached.hasError()) {
            return rebuildCache(cacheName, dbLoader, cacheWriter);
        }

        String lockKey = lockKey(cacheKey);
        String lockValue = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < MAX_LOCK_RETRIES; attempt++) {
            LockAttempt lockAttempt = tryLock(lockKey, lockValue, cacheName);
            if (lockAttempt == LockAttempt.ACQUIRED) {
                try {
                    cached = cacheReader.get();
                    if (cached.hit()) {
                        return cached.value();
                    }
                    return rebuildCache(cacheName, dbLoader, cacheWriter);
                } finally {
                    releaseLock(lockKey, lockValue, cacheName);
                }
            }
            if (lockAttempt == LockAttempt.ERROR) {
                return rebuildCache(cacheName, dbLoader, cacheWriter);
            }
            if (!sleepBeforeRetry()) {
                break;
            }
            cached = cacheReader.get();
            if (cached.hit()) {
                return cached.value();
            }
            if (cached.hasError()) {
                return rebuildCache(cacheName, dbLoader, cacheWriter);
            }
        }

        incrementMetric(METRIC_LOCK, cacheName, RESULT_TIMEOUT);
        return rebuildCache(cacheName, dbLoader, cacheWriter);
    }

    private <T> T rebuildCache(String cacheName, Supplier<T> dbLoader, Consumer<T> cacheWriter) {
        try {
            T value = dbLoader.get();
            cacheWriter.accept(value);
            incrementMetric(METRIC_REBUILD, cacheName, hasValue(value) ? RESULT_DB_HIT : RESULT_DB_MISS);
            return value;
        } catch (RuntimeException e) {
            incrementMetric(METRIC_REBUILD, cacheName, RESULT_ERROR);
            throw e;
        }
    }

    private <T> CacheLookupResult<T> readObjectCache(String key, Class<T> type, String cacheName) {
        Object cached;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Redis 读取失败，降级到 DB: key={}", key, e);
            incrementMetric(METRIC_READ, cacheName, RESULT_ERROR);
            return CacheLookupResult.error();
        }

        if (NULL_MARKER.equals(cached)) {
            incrementMetric(METRIC_READ, cacheName, RESULT_NEGATIVE_HIT);
            return CacheLookupResult.hit(null);
        }
        if (type.isInstance(cached)) {
            incrementMetric(METRIC_READ, cacheName, RESULT_HIT);
            return CacheLookupResult.hit(type.cast(cached));
        }
        incrementMetric(METRIC_READ, cacheName, RESULT_MISS);
        return CacheLookupResult.miss();
    }

    private CacheLookupResult<List<OauthProvider>> readProvidersCache(String key) {
        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Redis providers 读取失败，降级到 DB: key={}", key, e);
            incrementMetric(METRIC_READ, CACHE_PROVIDERS, RESULT_ERROR);
            return CacheLookupResult.error();
        }

        if (cached == null) {
            incrementMetric(METRIC_READ, CACHE_PROVIDERS, RESULT_MISS);
            return CacheLookupResult.miss();
        }
        if (NULL_MARKER.equals(cached)) {
            incrementMetric(METRIC_READ, CACHE_PROVIDERS, RESULT_NEGATIVE_HIT);
            return CacheLookupResult.hit(List.of());
        }

        try {
            List<OauthProvider> providers = parseProviders(cached);
            incrementMetric(METRIC_READ, CACHE_PROVIDERS, RESULT_HIT);
            return CacheLookupResult.hit(providers);
        } catch (IllegalArgumentException e) {
            log.warn("Redis providers 缓存非法，已删除: key={}", key);
            deleteCached(key, CACHE_PROVIDERS);
            incrementMetric(METRIC_READ, CACHE_PROVIDERS, RESULT_MISS);
            return CacheLookupResult.miss();
        }
    }

    private void putToCache(String key, Object value, String cacheName) {
        try {
            if (value == null) {
                redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_CACHE_TTL);
            } else {
                redisTemplate.opsForValue().set(key, value, randomTtl());
            }
        } catch (RuntimeException e) {
            log.warn("Redis 写入失败: key={}", key, e);
            incrementMetric(METRIC_REBUILD, cacheName, RESULT_ERROR);
        }
    }

    private void putProvidersToCache(String key, List<OauthProvider> providers) {
        try {
            if (providers.isEmpty()) {
                stringRedisTemplate.opsForValue().set(key, NULL_MARKER, NULL_CACHE_TTL);
                return;
            }
            String cached = String.join(",", providers.stream()
                    .map(OauthProvider::name)
                    .toList());
            stringRedisTemplate.opsForValue().set(key, cached, randomTtl());
        } catch (RuntimeException e) {
            log.warn("Redis providers 写入失败: key={}", key, e);
            incrementMetric(METRIC_REBUILD, CACHE_PROVIDERS, RESULT_ERROR);
        }
    }

    private List<OauthProvider> parseProviders(String cached) {
        if (cached.isBlank()) {
            return List.of();
        }
        return Stream.of(cached.split(","))
                .map(OauthProvider::valueOf)
                .toList();
    }

    private void deleteCached(String key, String cacheName) {
        try {
            redisTemplate.delete(key);
            incrementMetric(METRIC_EVICT, cacheName, RESULT_SUCCESS);
        } catch (RuntimeException e) {
            log.warn("Redis 删除失败: key={}", key, e);
            incrementMetric(METRIC_EVICT, cacheName, RESULT_ERROR);
        }
    }

    private LockAttempt tryLock(String lockKey, String lockValue, String cacheName) {
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
            if (Boolean.TRUE.equals(locked)) {
                incrementMetric(METRIC_LOCK, cacheName, RESULT_ACQUIRED);
                return LockAttempt.ACQUIRED;
            }
            incrementMetric(METRIC_LOCK, cacheName, RESULT_BUSY);
            return LockAttempt.BUSY;
        } catch (RuntimeException e) {
            log.warn("Redis 获取缓存重建锁失败，降级到 DB: key={}", lockKey, e);
            incrementMetric(METRIC_LOCK, cacheName, RESULT_ERROR);
            return LockAttempt.ERROR;
        }
    }

    private void releaseLock(String lockKey, String lockValue, String cacheName) {
        try {
            // 只释放当前请求持有的锁，避免误删其他请求新拿到的锁。
            Long released = stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockValue);
            if (released > 0) {
                incrementMetric(METRIC_LOCK, cacheName, RESULT_RELEASED);
            }
        } catch (RuntimeException e) {
            log.warn("Redis 释放缓存重建锁失败: key={}", lockKey, e);
            incrementMetric(METRIC_LOCK, cacheName, RESULT_RELEASE_ERROR);
        }
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(LOCK_RETRY_SLEEP.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean hasValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return value != null;
    }

    private void incrementMetric(String name, String cacheName, String result) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            meterRegistry.counter(name, Tags.of("cache", cacheName, "result", result)).increment();
        }
    }

    private Duration randomTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, CACHE_JITTER.getSeconds());
        return CACHE_TTL.plusSeconds(jitterSeconds);
    }

    private void evictUserCache(UUID userId, String previousEmail, String email) {
        deleteCached(userKey(userId), CACHE_USER);
        deleteCached(providersKey(userId), CACHE_PROVIDERS);
        if (previousEmail != null && !previousEmail.isBlank()) {
            deleteCached(emailKey(previousEmail), CACHE_EMAIL);
        }
        if (email != null && !email.isBlank()) {
            deleteCached(emailKey(email), CACHE_EMAIL);
        }
    }

    private void evictIdentityCache(UserIdentity identity) {
        deleteCached(identityKey(identity.getProvider(), identity.getProviderUserId()), CACHE_IDENTITY);
        deleteCached(providersKey(identity.getUserId()), CACHE_PROVIDERS);
        deleteCached(userKey(identity.getUserId()), CACHE_USER);
    }

    private String lockKey(String cacheKey) {
        return LOCK_KEY_PREFIX + cacheKey;
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

    private String emailKey(String email) {
        return EMAIL_KEY_PREFIX + email;
    }

    private enum CacheStatus {
        /**
         * Cached value was found.
         */
        HIT,
        /**
         * Cached value was not found.
         */
        MISS,
        /**
         * Cache lookup failed.
         */
        ERROR
    }

    private enum LockAttempt {
        /**
         * Cache rebuild lock was acquired.
         */
        ACQUIRED,
        /**
         * Cache rebuild lock is held by another caller.
         */
        BUSY,
        /**
         * Cache rebuild lock attempt failed.
         */
        ERROR
    }

    private record CacheLookupResult<T>(CacheStatus status, T value) {
        private static <T> CacheLookupResult<T> hit(T value) {
            return new CacheLookupResult<>(CacheStatus.HIT, value);
        }

        private static <T> CacheLookupResult<T> miss() {
            return new CacheLookupResult<>(CacheStatus.MISS, null);
        }

        private static <T> CacheLookupResult<T> error() {
            return new CacheLookupResult<>(CacheStatus.ERROR, null);
        }

        private boolean hit() {
            return status == CacheStatus.HIT;
        }

        private boolean hasError() {
            return status == CacheStatus.ERROR;
        }
    }
}
