package com.dayz.sc.course.service;

import com.dayz.sc.course.config.LiveKitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Best-effort LiveKit room administration.
 *
 * @author DaYZ
 * @since 2026-06-21
 */
@Service
@RequiredArgsConstructor
public class LiveKitRoomService {

    private final LiveKitProperties properties;
    private final LiveKitTokenService liveKitTokenService;
    private final RestClient restClient = RestClient.create();

    public void deleteRoom(String roomName) {
        if (!properties.configured() || roomName == null || roomName.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri(roomServiceUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + liveKitTokenService.createRoomAdminToken(roomName))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("room", roomName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
        }
    }

    private String roomServiceUrl() {
        String baseUrl = properties.getServerUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = properties.getUrl();
        }
        baseUrl = baseUrl.trim();
        if (baseUrl.startsWith("wss://")) {
            baseUrl = "https://" + baseUrl.substring("wss://".length());
        } else if (baseUrl.startsWith("ws://")) {
            baseUrl = "http://" + baseUrl.substring("ws://".length());
        }
        return baseUrl.replaceAll("/+$", "") + "/twirp/livekit.RoomService/DeleteRoom";
    }
}
