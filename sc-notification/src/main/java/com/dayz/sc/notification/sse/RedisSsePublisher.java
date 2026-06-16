package com.dayz.sc.notification.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

/**
 * 事件发布者。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSsePublisher {

    private static final ChannelTopic TOPIC = new ChannelTopic("sc:notification:sse");

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(SseMessage message) {
        try {
            redisTemplate.convertAndSend(TOPIC.getTopic(), message);
            log.debug("Published SSE message to Redis channel, targetUserId={}", message.targetUserId());
        } catch (Exception e) {
            log.error("Failed to publish SSE message to Redis", e);
        }
    }
}
