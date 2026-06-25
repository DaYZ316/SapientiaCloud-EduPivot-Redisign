package com.dayz.sc.ai.config;

import com.dayz.sc.ai.websocket.LiveSummaryAudioHandshakeInterceptor;
import com.dayz.sc.ai.websocket.LiveSummaryAudioWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class LiveSummaryWebSocketConfig implements WebSocketConfigurer {

    private final LiveSummaryAudioWebSocketHandler liveSummaryAudioWebSocketHandler;
    private final LiveSummaryAudioHandshakeInterceptor liveSummaryAudioHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveSummaryAudioWebSocketHandler, "/api/ai/live-summaries/class-sessions/{sessionId}/audio")
                .addInterceptors(liveSummaryAudioHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
