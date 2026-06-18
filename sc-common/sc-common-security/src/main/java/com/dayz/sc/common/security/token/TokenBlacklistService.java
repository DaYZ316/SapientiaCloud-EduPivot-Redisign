package com.dayz.sc.common.security.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * JWT Token 黑名单服务
 * <p>
 * 用于登出时将 token 的 JTI 加入黑名单，防止已签发的 token 继续使用
 * Key 格式: {@code auth:blacklist:{jti}} → "1"
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Slf4j
public record TokenBlacklistService(RedisTemplate<String, Object> redisTemplate) {

    private static final String KEY_PREFIX = "auth:blacklist:";

    /**
     * 将 token JTI 加入黑名单
     *
     * @param jti        JWT ID（jti claim）
     * @param ttlSeconds 过期时间（秒），应等于 token 剩余有效期
     */
    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
        log.debug("Token 已加入黑名单: jti={}", jti);
    }

    /**
     * 检查 token JTI 是否在黑名单中
     *
     * @param jti JWT ID
     * @return true 如果已被拉黑
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
