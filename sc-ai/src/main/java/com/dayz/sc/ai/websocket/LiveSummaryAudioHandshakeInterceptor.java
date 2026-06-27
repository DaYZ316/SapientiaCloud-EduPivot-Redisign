package com.dayz.sc.ai.websocket;

import com.dayz.sc.ai.service.LiveSummaryAudioTokenService;
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
 * LiveSummaryAudioHandshakeInterceptor.
 *
 * @author DaYZ
 */
@Component
public class LiveSummaryAudioHandshakeInterceptor implements HandshakeInterceptor {

    public static final String CLASS_SESSION_ID_ATTRIBUTE = "classSessionId";
    public static final String SUMMARY_SESSION_ID_ATTRIBUTE = "summarySessionId";
    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String ROLE_ATTRIBUTE = "role";

    private final LiveSummaryAudioTokenService tokenService;

    public LiveSummaryAudioHandshakeInterceptor(LiveSummaryAudioTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        String classSessionIdValue = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("sessionId");
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        if (classSessionIdValue == null || token == null) {
            return false;
        }
        UUID classSessionId;
        try {
            classSessionId = UUID.fromString(classSessionIdValue);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return tokenService.consumeToken(token)
                .filter(payload -> payload.classSessionId().equals(classSessionId))
                .map(payload -> {
                    attributes.put(CLASS_SESSION_ID_ATTRIBUTE, payload.classSessionId());
                    attributes.put(SUMMARY_SESSION_ID_ATTRIBUTE, payload.summarySessionId());
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
