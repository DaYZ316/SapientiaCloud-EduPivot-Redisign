package com.dayz.sc.course.sse;

import com.dayz.sc.course.model.vo.LivePracticeEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LivePracticeSseEmitter {

    private static final long NO_TIMEOUT = 0L;

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(error -> emitters.remove(userId, emitter));

        SseEmitter oldEmitter = emitters.put(userId, emitter);
        if (oldEmitter != null) {
            oldEmitter.complete();
        }

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException exception) {
            emitters.remove(userId, emitter);
        }
        return emitter;
    }

    public void sendToUsers(Collection<UUID> userIds, LivePracticeEventVO event) {
        Set<UUID> distinctUserIds = Set.copyOf(userIds);
        distinctUserIds.forEach(userId -> sendToUser(userId, event));
    }

    private void sendToUser(UUID userId, LivePracticeEventVO event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("live-practice").data(event));
        } catch (IOException exception) {
            log.debug("Live practice SSE failed for user {}", userId, exception);
            emitters.remove(userId, emitter);
        }
    }
}
