package com.dayz.sc.common.redis.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class KafkaIdempotencyGuard {

    private static final String KEY_PREFIX = "kafka:idempotent:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试获取事件处理权。返回 true 表示该事件首次处理，false 表示重复事件应跳过。
     */
    public boolean tryAcquire(String groupId, UUID eventId) {
        String key = KEY_PREFIX + groupId + ":" + eventId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
        return Boolean.TRUE.equals(acquired);
    }
}
