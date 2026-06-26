package com.dayz.sc.course.sse;

import com.dayz.sc.course.model.vo.ClassBarrageVO;
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
 * In-process SSE broadcaster for class barrage messages.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Slf4j
@Component
public class ClassBarrageSseEmitter {

    private static final long NO_TIMEOUT = 0L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = new ScheduledThreadPoolExecutor(1,
            r -> {
                Thread t = new Thread(r, "class-barrage-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public ClassBarrageSseEmitter() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public SseEmitter createEmitter(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ignored -> remove(sessionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException e) {
            remove(sessionId, emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void broadcast(UUID sessionId, ClassBarrageVO barrage) {
        Set<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters == null || sessionEmitters.isEmpty()) {
            return;
        }
        sessionEmitters.removeIf(emitter -> !send(emitter, barrage));
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private void sendHeartbeat() {
        emitters.forEach((sessionId, sessionEmitters) ->
                sessionEmitters.forEach(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                    } catch (IOException | IllegalStateException exception) {
                        log.debug("Class barrage SSE heartbeat failed for session {}", sessionId, exception);
                        remove(sessionId, emitter);
                        emitter.completeWithError(exception);
                    }
                }));
    }

    private boolean send(SseEmitter emitter, ClassBarrageVO barrage) {
        try {
            emitter.send(SseEmitter.event().name("barrage").data(barrage));
            return true;
        } catch (IOException | IllegalStateException exception) {
            log.debug("Class barrage SSE send failed", exception);
            emitter.completeWithError(exception);
            return false;
        }
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        Set<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters == null) {
            return;
        }
        sessionEmitters.remove(emitter);
        if (sessionEmitters.isEmpty()) {
            emitters.remove(sessionId);
        }
    }
}
