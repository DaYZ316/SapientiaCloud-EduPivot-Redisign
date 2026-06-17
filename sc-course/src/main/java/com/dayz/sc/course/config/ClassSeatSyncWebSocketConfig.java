package com.dayz.sc.course.config;

import com.dayz.sc.course.websocket.ClassSeatSyncHandshakeInterceptor;
import com.dayz.sc.course.websocket.ClassSeatSyncWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for classroom seat synchronization.
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ClassSeatSyncWebSocketConfig implements WebSocketConfigurer {

    private final ClassSeatSyncWebSocketHandler classSeatSyncWebSocketHandler;
    private final ClassSeatSyncHandshakeInterceptor classSeatSyncHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(classSeatSyncWebSocketHandler, "/api/class-sessions/seats/ws")
                .addInterceptors(classSeatSyncHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
