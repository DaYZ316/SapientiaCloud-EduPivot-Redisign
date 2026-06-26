package com.dayz.sc.course.sse;

import com.dayz.sc.course.model.vo.LivePracticeEventVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 随堂练习SSE推送管理器
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Slf4j
@Component
public class LivePracticeSseEmitter {

    private static final long NO_TIMEOUT = 0L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = new ScheduledThreadPoolExecutor(1,
            r -> {
                Thread t = new Thread(r, "live-practice-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public LivePracticeSseEmitter() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));

        emitters.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException exception) {
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void sendToUsers(Collection<UUID> userIds, LivePracticeEventVO event) {
        Set<UUID> distinctUserIds = Set.copyOf(userIds);
        distinctUserIds.forEach(userId -> sendToUser(userId, event));
    }

    private void sendToUser(UUID userId, LivePracticeEventVO event) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.forEach(emitter -> sendLivePractice(userId, emitter, event));
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private void sendHeartbeat() {
        emitters.forEach((userId, userEmitters) ->
                userEmitters.forEach(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                    } catch (IOException | IllegalStateException exception) {
                        log.debug("Live practice SSE heartbeat failed for user {}", userId, exception);
                        remove(userId, emitter);
                        emitter.completeWithError(exception);
                    }
                }));
    }

    private void sendLivePractice(UUID userId, SseEmitter emitter, LivePracticeEventVO event) {
        try {
            emitter.send(SseEmitter.event().name("live-practice").data(event));
        } catch (IOException | IllegalStateException exception) {
            log.debug("Live practice SSE failed for user {}", userId, exception);
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
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
}
