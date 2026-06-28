package com.dayz.sc.auth.repository.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dayz.sc.auth.mapper.UserIdentityMapper;
import com.dayz.sc.auth.mapper.UserMapper;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MybatisUserAccountRepositoryTest {

    private static final String READ_METRIC = "redis.auth.cache.read";
    private static final String REBUILD_METRIC = "redis.auth.cache.rebuild";
    private static final String LOCK_METRIC = "redis.auth.cache.lock";
    private static final String EVICT_METRIC = "redis.auth.cache.evict";

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserIdentityMapper userIdentityMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider;

    private SimpleMeterRegistry meterRegistry;
    private MybatisUserAccountRepository repository;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, UserIdentity.class);
    }

    private static UserIdentity identity(UUID userId, OauthProvider provider) {
        UserIdentity identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        return identity;
    }

    private static User user(UUID userId, String email) {
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        return user;
    }

    private static String userKey(UUID userId) {
        return "auth:user:" + userId;
    }

    private static String providersKey(UUID userId) {
        return userKey(userId) + ":providers";
    }

    private static String emailKey(String email) {
        return "auth:user:email:" + email;
    }

    private static String lockKey(String cacheKey) {
        return "lock:" + cacheKey;
    }

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        repository = new MybatisUserAccountRepository(
                userMapper,
                userIdentityMapper,
                redisTemplate,
                stringRedisTemplate,
                meterRegistryProvider
        );
    }

    @Test
    void findUser_shouldRebuildCacheAndReleaseLockWhenCacheMissesAndLockAcquired() {
        UUID userId = UUID.randomUUID();
        String cacheKey = userKey(userId);
        String lockKey = lockKey(cacheKey);
        User user = user(userId, "student@example.com");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey), anyString(), eq(Duration.ofSeconds(3)))).thenReturn(true);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(stringRedisTemplate.execute(any(), eq(List.of(lockKey)), any(Object[].class))).thenReturn(1L);

        Optional<User> result = repository.findUser(userId);

        assertThat(result).contains(user);
        verify(userMapper).selectById(userId);
        verify(valueOperations).set(eq(cacheKey), eq(user), any(Duration.class));
        verify(stringRedisTemplate).execute(any(), eq(List.of(lockKey)), any(Object[].class));
        assertMetric(READ_METRIC, "user", "miss", 2);
        assertMetric(LOCK_METRIC, "user", "acquired", 1);
        assertMetric(LOCK_METRIC, "user", "released", 1);
        assertMetric(REBUILD_METRIC, "user", "db_hit", 1);
    }

    @Test
    void findUser_shouldReadCacheAgainAndSkipDbWhenLockIsBusyThenCacheHits() {
        UUID userId = UUID.randomUUID();
        String cacheKey = userKey(userId);
        User user = user(userId, "student@example.com");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null, user);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey(cacheKey)), anyString(), eq(Duration.ofSeconds(3))))
                .thenReturn(false);

        Optional<User> result = repository.findUser(userId);

        assertThat(result).contains(user);
        verifyNoInteractions(userMapper);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        assertMetric(READ_METRIC, "user", "miss", 1);
        assertMetric(READ_METRIC, "user", "hit", 1);
        assertMetric(LOCK_METRIC, "user", "busy", 1);
        assertMetric(LOCK_METRIC, "user", "timeout", 0);
    }

    @Test
    void findUser_shouldFallbackToDbAndRecordTimeoutWhenLockStaysBusy() {
        UUID userId = UUID.randomUUID();
        String cacheKey = userKey(userId);
        User user = user(userId, "student@example.com");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey(cacheKey)), anyString(), eq(Duration.ofSeconds(3))))
                .thenReturn(false);
        when(userMapper.selectById(userId)).thenReturn(user);

        Optional<User> result = repository.findUser(userId);

        assertThat(result).contains(user);
        verify(userMapper).selectById(userId);
        verify(valueOperations).set(eq(cacheKey), eq(user), any(Duration.class));
        assertMetric(LOCK_METRIC, "user", "busy", 3);
        assertMetric(LOCK_METRIC, "user", "timeout", 1);
        assertMetric(REBUILD_METRIC, "user", "db_hit", 1);
    }

    @Test
    void findByEmail_shouldCacheNullMarkerWhenDbReturnsEmpty() {
        String email = "missing@example.com";
        String cacheKey = emailKey(email);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.setIfAbsent(eq(lockKey(cacheKey)), anyString(), eq(Duration.ofSeconds(3))))
                .thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(stringRedisTemplate.execute(any(), eq(List.of(lockKey(cacheKey))), any(Object[].class)))
                .thenReturn(1L);

        Optional<User> result = repository.findByEmail(email);

        assertThat(result).isEmpty();
        verify(valueOperations).set(cacheKey, "__NULL__", Duration.ofMinutes(2));
        assertMetric(REBUILD_METRIC, "email", "db_miss", 1);
        assertMetric(LOCK_METRIC, "email", "released", 1);
    }

    @Test
    void findByEmail_shouldFallbackToDbWhenRedisReadFails() {
        String email = "student@example.com";
        User user = user(UUID.randomUUID(), email);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(emailKey(email))).thenThrow(new RuntimeException("redis down"));
        when(userMapper.selectOne(any())).thenReturn(user);

        Optional<User> result = repository.findByEmail(email);

        assertThat(result).contains(user);
        verify(stringRedisTemplate, never()).opsForValue();
        verify(valueOperations).set(eq(emailKey(email)), eq(user), any(Duration.class));
        assertMetric(READ_METRIC, "email", "error", 1);
        assertMetric(REBUILD_METRIC, "email", "db_hit", 1);
    }

    @Test
    void saveUser_shouldContinueAndRecordErrorWhenRedisDeleteFails() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "new@example.com");
        when(userMapper.selectById(userId)).thenReturn(user(userId, "old@example.com"));
        when(userMapper.updateById(user)).thenReturn(1);
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete("auth:user:" + userId);

        User saved = repository.saveUser(user);

        assertThat(saved).isSameAs(user);
        verify(redisTemplate).delete("auth:user:" + userId);
        verify(redisTemplate).delete("auth:user:" + userId + ":providers");
        verify(redisTemplate).delete("auth:user:email:old@example.com");
        verify(redisTemplate).delete("auth:user:email:new@example.com");
        assertMetric(EVICT_METRIC, "user", "error", 1);
        assertMetric(EVICT_METRIC, "providers", "success", 1);
        assertMetric(EVICT_METRIC, "email", "success", 2);
    }

    @Test
    void findLinkedProviders_shouldReturnProvidersFromStringCache() {
        UUID userId = UUID.randomUUID();
        String cacheKey = providersKey(userId);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get(cacheKey)).thenReturn("GOOGLE,GITHUB");

        List<OauthProvider> providers = repository.findLinkedProviders(userId);

        assertThat(providers).containsExactly(OauthProvider.GOOGLE, OauthProvider.GITHUB);
        verifyNoInteractions(userIdentityMapper);
        assertMetric(READ_METRIC, "providers", "hit", 1);
    }

    @Test
    void findLinkedProviders_shouldWriteProvidersAsStringCacheWhenCacheMisses() {
        UUID userId = UUID.randomUUID();
        String cacheKey = providersKey(userId);
        String lockKey = lockKey(cacheKey);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(stringValueOperations.setIfAbsent(eq(lockKey), anyString(), eq(Duration.ofSeconds(3)))).thenReturn(true);
        when(stringRedisTemplate.execute(any(), eq(List.of(lockKey)), any(Object[].class))).thenReturn(1L);
        when(userIdentityMapper.selectList(any()))
                .thenReturn(List.of(identity(userId, OauthProvider.GOOGLE), identity(userId, OauthProvider.LOCAL)));

        List<OauthProvider> providers = repository.findLinkedProviders(userId);

        assertThat(providers).containsExactly(OauthProvider.GOOGLE, OauthProvider.LOCAL);
        verify(stringValueOperations).set(eq(cacheKey), eq("GOOGLE,LOCAL"), any(Duration.class));
        assertMetric(REBUILD_METRIC, "providers", "db_hit", 1);
    }

    @Test
    void findLinkedProviders_shouldEvictInvalidProviderCacheAndRebuildFromDb() {
        UUID userId = UUID.randomUUID();
        String cacheKey = providersKey(userId);
        String lockKey = lockKey(cacheKey);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get(cacheKey)).thenReturn("[\"legacy-json\"]").thenReturn(null);
        when(stringValueOperations.setIfAbsent(eq(lockKey), anyString(), eq(Duration.ofSeconds(3)))).thenReturn(true);
        when(stringRedisTemplate.execute(any(), eq(List.of(lockKey)), any(Object[].class))).thenReturn(1L);
        when(userIdentityMapper.selectList(any())).thenReturn(List.of(identity(userId, OauthProvider.LOCAL)));

        List<OauthProvider> providers = repository.findLinkedProviders(userId);

        assertThat(providers).containsExactly(OauthProvider.LOCAL);
        verify(redisTemplate).delete(cacheKey);
        verify(stringValueOperations).set(eq(cacheKey), eq("LOCAL"), any(Duration.class));
        assertMetric(EVICT_METRIC, "providers", "success", 1);
    }

    @Test
    void saveUser_shouldEvictPreviousAndCurrentEmailCache() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "new@example.com");
        when(userMapper.selectById(userId)).thenReturn(user(userId, "old@example.com"));
        when(userMapper.updateById(user)).thenReturn(1);

        repository.saveUser(user);

        verify(redisTemplate).delete("auth:user:" + userId);
        verify(redisTemplate).delete("auth:user:" + userId + ":providers");
        verify(redisTemplate).delete("auth:user:email:old@example.com");
        verify(redisTemplate).delete("auth:user:email:new@example.com");
    }

    private void assertMetric(String name, String cache, String result, double expected) {
        Counter counter = meterRegistry.find(name)
                .tag("cache", cache)
                .tag("result", result)
                .counter();
        double count = counter == null ? 0 : counter.count();
        assertThat(count).isEqualTo(expected);
    }
}
