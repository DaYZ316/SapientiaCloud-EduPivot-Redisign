package com.dayz.sc.notification.sse;

import com.dayz.sc.notification.model.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

/**
 * RedisSseSubscriber 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSseSubscriber implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationSseEmitter sseEmitter;

    @Override
    public void onMessage(@NonNull Message message, byte @Nullable [] pattern) {
        try {
            RedisSerializer<?> valueSerializer = redisTemplate.getValueSerializer();
            if (valueSerializer == null) {
                log.warn("Redis value serializer is not configured, skipping SSE message");
                return;
            }
            Object rawValue = valueSerializer.deserialize(message.getBody());

            if (!(rawValue instanceof SseMessage sseMessage)) {
                log.warn("Received invalid SSE message type: {}", rawValue != null ? rawValue.getClass() : "null");
                return;
            }

            dispatchLocally(sseMessage);
        } catch (Exception e) {
            log.error("Failed to process SSE message from Redis", e);
        }
    }

    private void dispatchLocally(SseMessage sseMessage) {
        NotificationVO notification = sseMessage.notification();
        long unreadCount = sseMessage.unreadCount();

        if (sseMessage.isBroadcast()) {
            if (sseMessage.excludeUserId() != null) {
                sseEmitter.broadcastExceptLocally(sseMessage.excludeUserId(), notification);
            } else {
                sseEmitter.broadcastLocally(notification);
            }
        } else {
            sseEmitter.sendToUserLocally(sseMessage.targetUserId(), notification, unreadCount);
        }
    }
}
