package com.dayz.sc.auth.repository.impl;

import com.dayz.sc.auth.mapper.UserIdentityMapper;
import com.dayz.sc.auth.mapper.UserMapper;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisUserAccountRepositoryTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserIdentityMapper userIdentityMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    private MybatisUserAccountRepository repository;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserIdentity.class);
    }

    @BeforeEach
    void setUp() {
        repository = new MybatisUserAccountRepository(
                userMapper,
                userIdentityMapper,
                redisTemplate,
                stringRedisTemplate
        );
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
    }

    @Test
    void findLinkedProviders_shouldWriteProvidersAsStringCacheWhenCacheMisses() {
        UUID userId = UUID.randomUUID();
        String cacheKey = providersKey(userId);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get(cacheKey)).thenReturn(null);
        when(userIdentityMapper.selectList(any()))
                .thenReturn(List.of(identity(userId, OauthProvider.GOOGLE), identity(userId, OauthProvider.LOCAL)));

        List<OauthProvider> providers = repository.findLinkedProviders(userId);

        assertThat(providers).containsExactly(OauthProvider.GOOGLE, OauthProvider.LOCAL);
        verify(stringValueOperations).set(eq(cacheKey), eq("GOOGLE,LOCAL"), any(Duration.class));
    }

    @Test
    void findLinkedProviders_shouldEvictInvalidProviderCacheAndRebuildFromDb() {
        UUID userId = UUID.randomUUID();
        String cacheKey = providersKey(userId);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get(cacheKey)).thenReturn("[\"legacy-json\"]");
        when(userIdentityMapper.selectList(any())).thenReturn(List.of(identity(userId, OauthProvider.LOCAL)));

        List<OauthProvider> providers = repository.findLinkedProviders(userId);

        assertThat(providers).containsExactly(OauthProvider.LOCAL);
        verify(redisTemplate).delete(cacheKey);
        verify(stringValueOperations).set(eq(cacheKey), eq("LOCAL"), any(Duration.class));
    }

    private static UserIdentity identity(UUID userId, OauthProvider provider) {
        UserIdentity identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        return identity;
    }

    private static String providersKey(UUID userId) {
        return "auth:user:" + userId + ":providers";
    }
}
