package com.dayz.sc.common.security.token;

import com.dayz.sc.common.security.config.JwtProperties;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * Refresh Token 管理服务。
 * <p>
 * 基于 Redis 存储，支持创建、验证、轮转和吊销。
 * Key 格式: {@code auth:refresh:{token}} → "userId:role"
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Slf4j
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtl = jwtProperties.getRefreshTokenTtl();
    }

    /**
     * 为用户创建一个新的 Refresh Token。
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return 生成的 Refresh Token 字符串
     */
    public String createRefreshToken(String userId, Integer role) {
        String token = UuidV7Generator.generate().toString().replace("-", "");
        String key = KEY_PREFIX + token;
        String value = role != null ? userId + ":" + role : userId + ":";
        redisTemplate.opsForValue().set(key, value, refreshTokenTtl);
        log.debug("创建 Refresh Token: userId={}", userId);
        return token;
    }

    /**
     * 验证 Refresh Token 并返回关联的用户 ID。
     *
     * @param token Refresh Token
     * @return 用户 ID，如果 token 无效或已过期则返回 null
     */
    public String validateRefreshToken(String token) {
        String key = KEY_PREFIX + token;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        String stored = value.toString();
        int colonIdx = stored.indexOf(':');
        return colonIdx > 0 ? stored.substring(0, colonIdx) : stored;
    }

    /**
     * 从 Refresh Token 中获取用户角色。
     *
     * @param token Refresh Token
     * @return 角色值，如果不存在返回 null
     */
    public Integer getRoleFromToken(String token) {
        String key = KEY_PREFIX + token;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        String stored = value.toString();
        int colonIdx = stored.indexOf(':');
        if (colonIdx > 0 && colonIdx < stored.length() - 1) {
            try {
                return Integer.parseInt(stored.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 轮转 Refresh Token：删除旧 token，创建新 token（防重放攻击）。
     *
     * @param oldToken 旧的 Refresh Token
     * @param userId   用户 ID
     * @param role     用户角色
     * @return 新的 Refresh Token，如果旧 token 无效则返回 null
     */
    public String rotateRefreshToken(String oldToken, String userId, Integer role) {
        String oldKey = KEY_PREFIX + oldToken;
        Object storedValue = redisTemplate.opsForValue().get(oldKey);
        if (storedValue == null) {
            log.warn("Refresh Token 轮转失败：旧 token 不存在");
            return null;
        }
        String stored = storedValue.toString();
        String storedUserId = stored.contains(":") ? stored.substring(0, stored.indexOf(':')) : stored;
        if (!storedUserId.equals(userId)) {
            log.warn("Refresh Token 轮转失败：用户不匹配");
            return null;
        }
        // 删除旧 token
        redisTemplate.delete(oldKey);
        // 创建新 token
        return createRefreshToken(userId, role);
    }

    /**
     * 吊销指定 Refresh Token。
     *
     * @param token Refresh Token
     */
    public void revokeRefreshToken(String token) {
        String key = KEY_PREFIX + token;
        redisTemplate.delete(key);
        log.debug("吊销 Refresh Token");
    }

    /**
     * 吊销指定用户的所有 Refresh Token。
     *
     * @param userId 用户 ID
     */
    public void revokeAllUserTokens(String userId) {
        String pattern = KEY_PREFIX + "*";
        var keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int revoked = 0;
        for (String key : keys) {
            Object storedValue = redisTemplate.opsForValue().get(key);
            if (storedValue != null) {
                String stored = storedValue.toString();
                String storedUserId = stored.contains(":") ? stored.substring(0, stored.indexOf(':')) : stored;
                if (storedUserId.equals(userId)) {
                    redisTemplate.delete(key);
                    revoked++;
                }
            }
        }
        log.info("吊销用户所有 Refresh Token: userId={}, count={}", userId, revoked);
    }
}
