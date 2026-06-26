package com.dayz.sc.course.websocket;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.course.model.vo.ClassParticipantVO;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.service.ClassSessionService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.UUID;

/**
 * WebSocket endpoint for classroom seat snapshots and server-side broadcasts.
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@Component
public class ClassSeatSyncWebSocketHandler extends TextWebSocketHandler {

    private final ClassSessionService classSessionService;
    private final ClassSeatSyncWebSocketHub hub;

    public ClassSeatSyncWebSocketHandler(ClassSessionService classSessionService, ClassSeatSyncWebSocketHub hub) {
        this.classSessionService = classSessionService;
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        UUID classSessionId = attribute(session, ClassSeatSyncHandshakeInterceptor.SESSION_ID_ATTRIBUTE, UUID.class);
        UUID userId = attribute(session, ClassSeatSyncHandshakeInterceptor.USER_ID_ATTRIBUTE, UUID.class);
        Integer role = attribute(session, ClassSeatSyncHandshakeInterceptor.ROLE_ATTRIBUTE, Integer.class);
        if (classSessionId == null || userId == null || role == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        try {
            ClassSessionVO classSession = classSessionService.getSession(classSessionId, userId, role);
            List<ClassParticipantVO> participants = classSessionService.listParticipants(classSessionId, userId, role);
            hub.register(classSessionId, session);
            hub.sendSnapshot(session, classSessionId, participants, classSession);
        } catch (BusinessException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        unregister(session);
    }

    private void unregister(WebSocketSession session) {
        UUID classSessionId = attribute(session, ClassSeatSyncHandshakeInterceptor.SESSION_ID_ATTRIBUTE, UUID.class);
        if (classSessionId != null) {
            hub.unregister(classSessionId, session);
        }
    }

    private <T> @Nullable T attribute(WebSocketSession session, String name, Class<T> type) {
        Object value = session.getAttributes().get(name);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
