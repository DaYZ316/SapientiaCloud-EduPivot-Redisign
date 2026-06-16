package com.dayz.sc.course.sse;

import com.dayz.sc.course.model.vo.ClassBarrageVO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process SSE broadcaster for class barrage messages.
 */
@Component
public class ClassBarrageSseEmitter {

    private static final long NO_TIMEOUT = 0L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ignored -> remove(sessionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(sessionId, emitter);
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

    private boolean send(SseEmitter emitter, ClassBarrageVO barrage) {
        try {
            emitter.send(SseEmitter.event().name("barrage").data(barrage));
            return true;
        } catch (IOException e) {
            emitter.completeWithError(e);
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
