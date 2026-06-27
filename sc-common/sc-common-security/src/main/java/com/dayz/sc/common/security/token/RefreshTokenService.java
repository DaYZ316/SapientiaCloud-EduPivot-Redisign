package com.dayz.sc.common.security.token;

import com.dayz.sc.common.security.config.JwtProperties;
import com.dayz.sc.common.util.UuidV7Generator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Redis-backed Refresh Token service.
 * <p>
 * v2 keys store a SHA-256 token hash instead of the raw token:
 * {@code auth:refresh:v2:{tokenHash} -> "userId:role"} and
 * {@code auth:user-refresh:v2:{userId} -> Set<tokenHash>}.
 *
 * @author DaYZ
 */
@Slf4j
public record RefreshTokenService(RedisTemplate<String, Object> redisTemplate,
                                  StringRedisTemplate stringRedisTemplate,
                                  Duration refreshTokenTtl,
                                  @Nullable MeterRegistry meterRegistry) {

    private static final String LEGACY_KEY_PREFIX = "auth:refresh:";
    private static final String TOKEN_KEY_PREFIX = "auth:refresh:v2:";
    private static final String USER_TOKEN_KEY_PREFIX = "auth:user-refresh:v2:";
    private static final long LEGACY_SCAN_COUNT = 1_000L;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final RedisScript<@NonNull List<@NonNull Object>> ROTATE_SCRIPT = RedisScript.of("""
            local oldTokenKey = KEYS[1]
            local oldUserSetKey = KEYS[2]
            local newTokenKey = KEYS[3]
            local newUserSetKey = KEYS[4]
            local expectedUserId = ARGV[1]
            local newValue = ARGV[2]
            local ttlSeconds = tonumber(ARGV[3])
            local oldTokenHash = ARGV[4]
            local newTokenHash = ARGV[5]
            local stored = redis.call('GET', oldTokenKey)
            if not stored then
              return {0}
            end
            local colonIdx = string.find(stored, ':', 1, true)
            local storedUserId = stored
            if colonIdx and colonIdx > 1 then
              storedUserId = string.sub(stored, 1, colonIdx - 1)
            end
            if storedUserId ~= expectedUserId then
              return {0}
            end
            redis.call('DEL', oldTokenKey)
            redis.call('SREM', oldUserSetKey, oldTokenHash)
            redis.call('SET', newTokenKey, newValue, 'EX', ttlSeconds)
            redis.call('SADD', newUserSetKey, newTokenHash)
            redis.call('EXPIRE', newUserSetKey, ttlSeconds)
            return {1}
            """, (Class<List<Object>>) (Class) List.class);

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate, JwtProperties jwtProperties) {
        this(redisTemplate, new StringRedisTemplate(redisTemplate.getRequiredConnectionFactory()),
                jwtProperties.getRefreshTokenTtl(), null);
    }

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate,
                               StringRedisTemplate stringRedisTemplate,
                               JwtProperties jwtProperties,
                               @Nullable MeterRegistry meterRegistry) {
        this(redisTemplate, stringRedisTemplate, jwtProperties.getRefreshTokenTtl(), meterRegistry);
    }

    public String createRefreshToken(String userId, Integer role) {
        String token = UuidV7Generator.generate().toString().replace("-", "");
        String tokenHash = tokenHash(token);
        stringRedisTemplate.opsForValue().set(tokenKey(tokenHash), tokenValue(userId, role), refreshTokenTtl);
        stringRedisTemplate.opsForSet().add(userTokensKey(userId), tokenHash);
        stringRedisTemplate.expire(userTokensKey(userId), refreshTokenTtl);
        incrementMetric("redis.refresh_token.create", "result", "success");
        log.debug("Created Refresh Token: userId={}", userId);
        return token;
    }

    public String validateRefreshToken(String token) {
        TokenRecord tokenRecord = readToken(token);
        return tokenRecord == null ? null : tokenRecord.userId();
    }

    public Integer getRoleFromToken(String token) {
        TokenRecord tokenRecord = readToken(token);
        return tokenRecord == null ? null : tokenRecord.role();
    }

    public String rotateRefreshToken(String oldToken, String userId, Integer role) {
        String oldTokenHash = tokenHash(oldToken);
        String newToken = UuidV7Generator.generate().toString().replace("-", "");
        String newTokenHash = tokenHash(newToken);
        List<?> result = stringRedisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(tokenKey(oldTokenHash), userTokensKey(userId), tokenKey(newTokenHash), userTokensKey(userId)),
                userId,
                tokenValue(userId, role),
                String.valueOf(refreshTokenTtl.toSeconds()),
                oldTokenHash,
                newTokenHash
        );
        if (rotateSucceeded(result)) {
            incrementMetric("redis.refresh_token.rotate", "result", "success");
            return newToken;
        }

        TokenRecord legacyToken = readLegacyToken(oldToken);
        if (legacyToken == null) {
            log.warn("Refresh Token rotation failed: old token is missing");
            incrementMetric("redis.refresh_token.rotate", "result", "invalid");
            return null;
        }
        if (!legacyToken.userId().equals(userId)) {
            log.warn("Refresh Token rotation failed: user mismatch");
            incrementMetric("redis.refresh_token.rotate", "result", "mismatch");
            return null;
        }
        if (!Boolean.TRUE.equals(redisTemplate.delete(legacyTokenKey(oldToken)))) {
            incrementMetric("redis.refresh_token.rotate", "result", "legacy_invalid");
            return null;
        }
        incrementMetric("redis.refresh_token.rotate", "result", "legacy_success");
        return createRefreshToken(userId, role);
    }

    public void revokeRefreshToken(String token) {
        String tokenHash = tokenHash(token);
        TokenRecord tokenRecord = readToken(token);
        stringRedisTemplate.delete(tokenKey(tokenHash));
        redisTemplate.delete(legacyTokenKey(token));
        if (tokenRecord != null) {
            stringRedisTemplate.opsForSet().remove(userTokensKey(tokenRecord.userId()), tokenHash);
        }
        incrementMetric("redis.refresh_token.revoke", "scope", "single");
        log.debug("Revoked Refresh Token");
    }

    public void revokeAllUserTokens(String userId) {
        String userTokensKey = userTokensKey(userId);
        Set<String> tokenHashes = stringRedisTemplate.opsForSet().members(userTokensKey);
        List<String> keysToDelete = new ArrayList<>();
        if (tokenHashes != null) {
            tokenHashes.stream()
                    .map(this::tokenKey)
                    .forEach(keysToDelete::add);
        }
        keysToDelete.add(userTokensKey);

        int legacyRevoked = revokeLegacyUserTokens(userId);
        Long deleted = stringRedisTemplate.delete(keysToDelete);
        int revoked = (deleted == null ? 0 : deleted.intValue()) + legacyRevoked;
        incrementMetric("redis.refresh_token.revoke", "scope", "user");
        log.info("Revoked all user Refresh Tokens: userId={}, count={}", userId, revoked);
    }

    private TokenRecord readToken(String token) {
        String value = stringRedisTemplate.opsForValue().get(tokenKey(tokenHash(token)));
        if (value != null) {
            incrementMetric("redis.refresh_token.read", "version", "v2");
            return parseTokenRecord(value);
        }
        TokenRecord legacyToken = readLegacyToken(token);
        if (legacyToken != null) {
            incrementMetric("redis.refresh_token.read", "version", "legacy");
        }
        return legacyToken;
    }

    private TokenRecord readLegacyToken(String token) {
        Object value = redisTemplate.opsForValue().get(legacyTokenKey(token));
        return value == null ? null : parseTokenRecord(value);
    }

    private int revokeLegacyUserTokens(String userId) {
        int revoked = 0;
        ScanOptions options = ScanOptions.scanOptions()
                .match(LEGACY_KEY_PREFIX + "*")
                .count(LEGACY_SCAN_COUNT)
                .build();
        try (Cursor<@NonNull String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (key.startsWith(TOKEN_KEY_PREFIX)) {
                    continue;
                }
                Object value = redisTemplate.opsForValue().get(key);
                TokenRecord tokenRecord = value == null ? null : parseTokenRecord(value);
                if (tokenRecord != null && tokenRecord.userId().equals(userId)) {
                    redisTemplate.delete(key);
                    revoked++;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Legacy Refresh Token SCAN revoke failed: userId={}", userId, e);
            incrementMetric("redis.refresh_token.revoke", "scope", "legacy_scan_error");
        }
        return revoked;
    }

    private TokenRecord parseTokenRecord(Object value) {
        String stored = value.toString();
        int colonIdx = stored.indexOf(':');
        if (colonIdx <= 0) {
            return new TokenRecord(stored, null);
        }
        Integer role = null;
        if (colonIdx < stored.length() - 1) {
            try {
                role = Integer.parseInt(stored.substring(colonIdx + 1));
            } catch (NumberFormatException ignored) {
                // Keep role absent when the legacy token stored a malformed role value.
            }
        }
        return new TokenRecord(stored.substring(0, colonIdx), role);
    }

    private boolean rotateSucceeded(List<?> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        Object status = result.getFirst();
        if (status instanceof Number number) {
            return number.longValue() == 1L;
        }
        return "1".equals(String.valueOf(status));
    }

    private String tokenValue(String userId, Integer role) {
        return role != null ? userId + ":" + role : userId + ":";
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String userTokensKey(String userId) {
        return USER_TOKEN_KEY_PREFIX + userId;
    }

    private String legacyTokenKey(String token) {
        return LEGACY_KEY_PREFIX + token;
    }

    private String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void incrementMetric(String name, String tagKey, String tagValue) {
        if (meterRegistry != null) {
            meterRegistry.counter(name, Tags.of(tagKey, tagValue)).increment();
        }
    }

    private record TokenRecord(String userId, @Nullable Integer role) {
    }
}
