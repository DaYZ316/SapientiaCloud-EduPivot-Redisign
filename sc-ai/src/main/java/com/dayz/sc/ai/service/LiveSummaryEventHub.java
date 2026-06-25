package com.dayz.sc.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveSummaryEventHub {

    private final ObjectMapper objectMapper;
    private final Map<UUID, Sinks.Many<ServerSentEvent<String>>> sinks = new ConcurrentHashMap<>();

    public LiveSummaryEventHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> stream(UUID classSessionId) {
        return sink(classSessionId).asFlux();
    }

    public ServerSentEvent<String> event(String event, Object payload) {
        return ServerSentEvent.builder(toJson(payload))
                .event(event)
                .build();
    }

    public ServerSentEvent<String> keepalive() {
        return event("keepalive", Map.of("type", "keepalive"));
    }

    public void emit(UUID classSessionId, String event, Object payload) {
        sink(classSessionId).tryEmitNext(event(event, payload));
    }

    public void emitError(UUID classSessionId, String message) {
        emit(classSessionId, "error", Map.of("message", message));
    }

    private Sinks.Many<ServerSentEvent<String>> sink(UUID classSessionId) {
        return sinks.computeIfAbsent(classSessionId, ignored -> Sinks.many().multicast().directBestEffort());
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
