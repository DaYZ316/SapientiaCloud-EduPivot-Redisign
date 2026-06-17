package com.dayz.sc.course.websocket;

import com.dayz.sc.course.model.vo.ClassParticipantVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active classroom seat WebSocket sessions and broadcasts seat updates.
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@Component
@RequiredArgsConstructor
public class ClassSeatSyncWebSocketHub {

    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<WebSocketSession>> sessionsByClassSession = new ConcurrentHashMap<>();

    public void register(UUID classSessionId, WebSocketSession session) {
        sessionsByClassSession.computeIfAbsent(classSessionId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(UUID classSessionId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByClassSession.get(classSessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByClassSession.remove(classSessionId);
        }
    }

    public void sendSnapshot(WebSocketSession session, UUID classSessionId, List<ClassParticipantVO> participants) {
        send(session, new SeatSyncMessage("seat_snapshot", classSessionId, participants, null, null, null));
    }

    public void broadcastUpsert(UUID classSessionId, ClassParticipantVO participant) {
        broadcast(classSessionId, new SeatSyncMessage("seat_upsert", classSessionId, null, participant, null, null));
    }

    public void broadcastRemove(UUID classSessionId, UUID userId, Integer seatIndex) {
        broadcast(classSessionId, new SeatSyncMessage("seat_remove", classSessionId, null, null, userId, seatIndex));
    }

    private void broadcast(UUID classSessionId, SeatSyncMessage message) {
        Set<WebSocketSession> sessions = sessionsByClassSession.get(classSessionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            return;
        }
        sessions.removeIf(session -> !send(session, payload));
    }

    private void send(WebSocketSession session, SeatSyncMessage message) {
        try {
            send(session, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ignored) {
            // The payload is made of simple records and should always serialize.
        }
    }

    private boolean send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private record SeatSyncMessage(
            String type,
            UUID sessionId,
            List<ClassParticipantVO> participants,
            ClassParticipantVO participant,
            UUID userId,
            Integer seatIndex
    ) {
    }
}
