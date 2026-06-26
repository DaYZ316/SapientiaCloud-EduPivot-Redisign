package com.dayz.sc.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Tracks the opening teacher's live page presence with a short Redis TTL.
 */
@Service
@RequiredArgsConstructor
public class ClassLivePresenceService {

    private static final Duration TEACHER_LIVE_HEARTBEAT_TTL = Duration.ofSeconds(45);
    private static final String TEACHER_LIVE_HEARTBEAT_PREFIX = "class-live:teacher-heartbeat:";

    private final StringRedisTemplate stringRedisTemplate;

    public void heartbeat(UUID sessionId, UUID userId) {
        stringRedisTemplate.opsForValue().set(key(sessionId, userId), "1", TEACHER_LIVE_HEARTBEAT_TTL);
    }

    public boolean isPresent(UUID sessionId, UUID userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(sessionId, userId)));
    }

    private String key(UUID sessionId, UUID userId) {
        return TEACHER_LIVE_HEARTBEAT_PREFIX + sessionId + ":" + userId;
    }
}
