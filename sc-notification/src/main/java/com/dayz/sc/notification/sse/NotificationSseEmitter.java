package com.dayz.sc.notification.sse;

import com.dayz.sc.notification.model.vo.NotificationVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 通知 SSE 连接管理器
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Slf4j
@Component
public class NotificationSseEmitter {

    private static final long NO_TIMEOUT = 0L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final RedisSsePublisher redisSsePublisher;
    private final ScheduledExecutorService heartbeatExecutor = new ScheduledThreadPoolExecutor(1,
            r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public NotificationSseEmitter(RedisSsePublisher redisSsePublisher) {
        this.redisSsePublisher = redisSsePublisher;
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user: {}", userId);
            remove(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timeout for user: {}", userId);
            remove(userId, emitter);
        });

        emitter.onError(e -> {
            log.error("SSE connection error for user: {}", userId, e);
            remove(userId, emitter);
        });

        emitters.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to notification stream"));
        } catch (IOException | IllegalStateException e) {
            log.error("Failed to send initial SSE message for user: {}", userId, e);
            remove(userId, emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void sendToUser(UUID userId, NotificationVO notification) {
        redisSsePublisher.publish(new SseMessage(userId, null, notification));
    }

    /**
     * 发送通知给指定用户，附带精确的未读计数
     */
    public void sendToUser(UUID userId, NotificationVO notification, long unreadCount) {
        redisSsePublisher.publish(new SseMessage(userId, null, notification, unreadCount));
    }

    public void broadcast(NotificationVO notification) {
        redisSsePublisher.publish(new SseMessage(null, null, notification));
    }

    public void broadcastExcept(UUID excludeUserId, NotificationVO notification) {
        redisSsePublisher.publish(new SseMessage(null, excludeUserId, notification));
    }

    public void sendToUserLocally(UUID userId, NotificationVO notification) {
        sendToUserLocally(userId, notification, -1);
    }

    /**
     * 本地发送通知给指定用户，附带未读计数
     */
    public void sendToUserLocally(UUID userId, NotificationVO notification, long unreadCount) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        SsePayload payload = new SsePayload(notification, unreadCount);
        userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload));
            } catch (IOException | IllegalStateException e) {
                log.error("Failed to send SSE notification to user: {}", userId, e);
                remove(userId, emitter);
                emitter.completeWithError(e);
            }
        });
    }

    public void broadcastLocally(NotificationVO notification) {
        SsePayload payload = new SsePayload(notification, -1);
        emitters.forEach((userId, userEmitters) -> userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload));
            } catch (IOException | IllegalStateException e) {
                log.error("Failed to broadcast SSE notification to user: {}", userId, e);
                remove(userId, emitter);
                emitter.completeWithError(e);
            }
        }));
    }

    public void broadcastExceptLocally(UUID excludeUserId, NotificationVO notification) {
        SsePayload payload = new SsePayload(notification, -1);
        emitters.forEach((userId, userEmitters) -> {
            if (userId.equals(excludeUserId)) {
                return;
            }
            userEmitters.forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notification")
                            .data(payload));
                } catch (IOException | IllegalStateException e) {
                    log.error("Failed to broadcast SSE notification to user: {}", userId, e);
                    remove(userId, emitter);
                    emitter.completeWithError(e);
                }
            });
        });
    }

    public int getOnlineCount() {
        return emitters.size();
    }

    public boolean isUserOnline(UUID userId) {
        return emitters.containsKey(userId);
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private void sendHeartbeat() {
        emitters.forEach((userId, userEmitters) -> userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("ping"));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE heartbeat failed for user: {}", userId, e);
                remove(userId, emitter);
                emitter.completeWithError(e);
            }
        }));
    }

    private void remove(UUID userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
        }
    }

    /**
     * SSE 推送的 payload 结构，包含通知详情和未读计数
     */
    public record SsePayload(NotificationVO notification, long unreadCount) {
    }
}
