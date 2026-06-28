package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.course.config.LiveKitProperties;
import com.dayz.sc.course.model.vo.LiveKitTokenVO;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Creates LiveKit-compatible access tokens.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Service
@RequiredArgsConstructor
public class LiveKitTokenService {

    private final LiveKitProperties properties;

    public void requireConfigured() {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCodes.SERVICE_UNAVAILABLE, "LiveKit is not configured");
        }
    }

    public LiveKitTokenVO createToken(UUID userId, String roomName, boolean canPublish) {
        requireConfigured();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getApiKey())
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("video", Map.of(
                        "roomJoin", true,
                        "room", roomName,
                        "canPublish", canPublish,
                        "canPublishData", true,
                        "canSubscribe", true
                ))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(properties.getApiSecret()));
        } catch (JOSEException e) {
            throw new BusinessException(ErrorCodes.SERVICE_UNAVAILABLE, "LiveKit token signing failed");
        }
        return new LiveKitTokenVO(properties.getUrl(), roomName, jwt.serialize(), expiresAt);
    }

    public String createRoomAdminToken(String roomName) {
        requireConfigured();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(5));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getApiKey())
                .subject("sc-course-live-room-admin")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("video", Map.of(
                        "roomAdmin", true,
                        "room", roomName
                ))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(properties.getApiSecret()));
        } catch (JOSEException e) {
            throw new BusinessException(ErrorCodes.SERVICE_UNAVAILABLE, "LiveKit token signing failed");
        }
        return jwt.serialize();
    }
}
