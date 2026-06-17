package com.dayz.sc.course.websocket;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Validates one-time seat sync tokens before the WebSocket session is created.
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@Component
public class ClassSeatSyncHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_ID_ATTRIBUTE = "classSessionId";
    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String ROLE_ATTRIBUTE = "role";

    private final ClassSeatSyncTokenService tokenService;

    public ClassSeatSyncHandshakeInterceptor(ClassSeatSyncTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        String sessionIdValue = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("sessionId");
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        if (sessionIdValue == null || token == null) {
            return false;
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdValue);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return tokenService.consumeToken(token)
                .filter(payload -> payload.sessionId().equals(sessionId))
                .map(payload -> {
                    attributes.put(SESSION_ID_ATTRIBUTE, payload.sessionId());
                    attributes.put(USER_ID_ATTRIBUTE, payload.userId());
                    attributes.put(ROLE_ATTRIBUTE, payload.role());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               @Nullable Exception exception) {
        // No-op.
    }
}
