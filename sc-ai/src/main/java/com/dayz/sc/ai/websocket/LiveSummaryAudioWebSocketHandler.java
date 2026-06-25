package com.dayz.sc.ai.websocket;

import com.dayz.sc.ai.service.DashScopeAsrClient;
import com.dayz.sc.ai.service.LiveSummaryService;
import com.dayz.sc.common.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.UUID;

@Component
@Slf4j
public class LiveSummaryAudioWebSocketHandler extends AbstractWebSocketHandler {

    private static final String ASR_STREAM_ATTRIBUTE = "asrStream";

    private final LiveSummaryService liveSummaryService;

    public LiveSummaryAudioWebSocketHandler(LiveSummaryService liveSummaryService) {
        this.liveSummaryService = liveSummaryService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        UUID classSessionId = attribute(session, LiveSummaryAudioHandshakeInterceptor.CLASS_SESSION_ID_ATTRIBUTE, UUID.class);
        UUID summarySessionId = attribute(session, LiveSummaryAudioHandshakeInterceptor.SUMMARY_SESSION_ID_ATTRIBUTE, UUID.class);
        UUID userId = attribute(session, LiveSummaryAudioHandshakeInterceptor.USER_ID_ATTRIBUTE, UUID.class);
        Integer role = attribute(session, LiveSummaryAudioHandshakeInterceptor.ROLE_ATTRIBUTE, Integer.class);
        if (classSessionId == null || summarySessionId == null || userId == null || role == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        try {
            DashScopeAsrClient.LiveSummaryAsrStream asrStream = liveSummaryService.openAudioStream(
                    classSessionId, summarySessionId, userId, role);
            session.getAttributes().put(ASR_STREAM_ATTRIBUTE, asrStream);
        } catch (BusinessException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason(exception.getMessage()));
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) {
        DashScopeAsrClient.LiveSummaryAsrStream asrStream = attribute(
                session, ASR_STREAM_ATTRIBUTE, DashScopeAsrClient.LiveSummaryAsrStream.class);
        if (asrStream == null) {
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] audio = new byte[payload.remaining()];
        payload.get(audio);
        asrStream.sendAudio(audio);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        // Binary PCM chunks carry the classroom audio. Text messages are ignored for now.
    }

    @Override
    protected void handlePongMessage(@NonNull WebSocketSession session, @NonNull PongMessage message) {
        // No-op.
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        log.debug("Live summary audio websocket failed", exception);
        closeAsrStream(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        closeAsrStream(session);
    }

    private void closeAsrStream(WebSocketSession session) {
        DashScopeAsrClient.LiveSummaryAsrStream asrStream = attribute(
                session, ASR_STREAM_ATTRIBUTE, DashScopeAsrClient.LiveSummaryAsrStream.class);
        if (asrStream != null) {
            asrStream.close();
        }
    }

    private <T> @Nullable T attribute(WebSocketSession session, String name, Class<T> type) {
        Object value = session.getAttributes().get(name);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
