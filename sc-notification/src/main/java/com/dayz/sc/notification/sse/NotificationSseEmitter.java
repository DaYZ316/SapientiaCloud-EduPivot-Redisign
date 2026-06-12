package com.dayz.sc.notification.sse;

import com.dayz.sc.notification.model.vo.NotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知 SSE 连接管理器。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Slf4j
@Component
public class NotificationSseEmitter {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final RedisSsePublisher redisSsePublisher;

    public NotificationSseEmitter(RedisSsePublisher redisSsePublisher) {
        this.redisSsePublisher = redisSsePublisher;
    }

    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timeout for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onError(e -> {
            log.error("SSE connection error for user: {}", userId, e);
            emitters.remove(userId);
        });

        emitters.put(userId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to notification stream"));
        } catch (IOException e) {
            log.error("Failed to send initial SSE message for user: {}", userId, e);
            emitters.remove(userId);
        }

        return emitter;
    }

    public void sendToUser(UUID userId, NotificationVO notification) {
        redisSsePublisher.publish(new SseMessage(userId, notification));
    }

    public void broadcast(NotificationVO notification) {
        redisSsePublisher.publish(new SseMessage(null, notification));
    }

    public void sendToUserLocally(UUID userId, NotificationVO notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
        } catch (IOException e) {
            log.error("Failed to send SSE notification to user: {}", userId, e);
            emitters.remove(userId);
        }
    }

    public void broadcastLocally(NotificationVO notification) {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.error("Failed to broadcast SSE notification to user: {}", userId, e);
                emitters.remove(userId);
            }
        });
    }

    public int getOnlineCount() {
        return emitters.size();
    }

    public boolean isUserOnline(UUID userId) {
        return emitters.containsKey(userId);
    }
}
