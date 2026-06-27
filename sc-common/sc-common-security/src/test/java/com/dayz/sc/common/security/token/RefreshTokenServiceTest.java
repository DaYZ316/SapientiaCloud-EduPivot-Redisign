package com.dayz.sc.common.security.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private ValueOperations<String, Object> objectValueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private Cursor<String> cursor;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(redisTemplate, stringRedisTemplate, Duration.ofDays(7), null);
    }

    @Test
    void createRefreshToken_shouldStoreHashedTokenAndUserIndex() {
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        String token = service.createRefreshToken("user-1", 2);

        assertThat(token).isNotBlank();
        verify(stringValueOperations).set(startsWith("auth:refresh:v2:"), eq("user-1:2"), eq(Duration.ofDays(7)));
        verify(setOperations).add(eq("auth:user-refresh:v2:user-1"), anyString());
        verify(stringRedisTemplate).expire("auth:user-refresh:v2:user-1", Duration.ofDays(7));
        verify(stringValueOperations, never()).set(eq("auth:refresh:" + token), anyString(), any());
    }

    @Test
    void validateRefreshToken_shouldPreferV2AndFallbackToLegacy() {
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(redisTemplate.opsForValue()).thenReturn(objectValueOperations);
        when(stringValueOperations.get(startsWith("auth:refresh:v2:"))).thenReturn(null);
        when(objectValueOperations.get("auth:refresh:legacy-token")).thenReturn("user-1:3");

        assertThat(service.validateRefreshToken("legacy-token")).isEqualTo("user-1");
        assertThat(service.getRoleFromToken("legacy-token")).isEqualTo(3);
    }

    @Test
    void revokeAllUserTokens_shouldUseUserIndexAndScanLegacyWithoutKeys() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:user-refresh:v2:user-1")).thenReturn(Set.of("hash-a", "hash-b"));
        when(redisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(stringRedisTemplate.delete(anyCollection())).thenReturn(3L);

        service.revokeAllUserTokens("user-1");

        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(stringRedisTemplate).delete(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsAll(List.of(
                "auth:refresh:v2:hash-a",
                "auth:refresh:v2:hash-b",
                "auth:user-refresh:v2:user-1"));
        verify(redisTemplate, never()).keys(any());
    }

    @Test
    void rotateRefreshToken_shouldReturnNewTokenOnlyWhenLuaSucceeds() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(1L));

        String newToken = service.rotateRefreshToken("old-token", "user-1", 1);

        assertThat(newToken).isNotBlank();
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                argThat(keys -> keys.size() == 4
                        && keys.get(0).startsWith("auth:refresh:v2:")
                        && keys.get(1).equals("auth:user-refresh:v2:user-1")),
                eq("user-1"),
                eq("user-1:1"),
                eq(String.valueOf(Duration.ofDays(7).toSeconds())),
                anyString(),
                anyString()
        );
    }

    @Test
    void rotateRefreshToken_shouldNotCreateNewTokenWhenLegacyDeleteFails() {
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(0L));
        when(redisTemplate.opsForValue()).thenReturn(objectValueOperations);
        when(objectValueOperations.get("auth:refresh:legacy-token")).thenReturn("user-1:1");
        when(redisTemplate.delete("auth:refresh:legacy-token")).thenReturn(false);

        String newToken = service.rotateRefreshToken("legacy-token", "user-1", 1);

        assertThat(newToken).isNull();
        verify(stringRedisTemplate, never()).opsForValue();
        verify(stringRedisTemplate, never()).opsForSet();
    }
}
