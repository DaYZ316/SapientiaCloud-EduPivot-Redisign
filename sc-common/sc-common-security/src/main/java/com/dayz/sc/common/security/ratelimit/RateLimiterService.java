package com.dayz.sc.common.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * 基于 Redis 滑动窗口的接口限流服务
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Slf4j
public class RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:";

    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local maxRequests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local windowStart = now - window * 1000
            redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)
            local count = redis.call('ZCARD', key)
            if count < maxRequests then
                redis.call('ZADD', key, now, now .. '-' .. math.random(100000))
                redis.call('EXPIRE', key, window)
                return 1
            else
                return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<@NonNull Long> redisScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.redisScript.setResultType(Long.class);
    }

    public boolean tryAcquire(String key, int windowSeconds, int maxRequests) {
        String redisKey = KEY_PREFIX + key;
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    redisScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests),
                    String.valueOf(now)
            );
            return result == 1L;
        } catch (Exception e) {
            log.warn("限流服务异常，放行请求: key={}", key, e);
            return true;
        }
    }
}
