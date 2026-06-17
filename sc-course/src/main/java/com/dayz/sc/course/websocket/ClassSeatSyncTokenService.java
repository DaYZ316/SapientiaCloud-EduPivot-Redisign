package com.dayz.sc.course.websocket;

import com.dayz.sc.course.model.vo.ClassSeatSyncTokenVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues short-lived one-time tokens for classroom seat WebSocket connections.
 *
 * @author DaYZ
 * @since 2026-06-17
 */
@Service
public class ClassSeatSyncTokenService {

    private static final long TOKEN_TTL_SECONDS = 60;
    private static final String TOKEN_KEY_PREFIX = "class-seat-sync:";
    private static final String TOKEN_SEPARATOR = "|";

    private final StringRedisTemplate stringRedisTemplate;

    public ClassSeatSyncTokenService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public ClassSeatSyncTokenVO issueToken(UUID sessionId, UUID userId, Integer role) {
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue()
                .set(tokenKey(token), sessionId + TOKEN_SEPARATOR + userId + TOKEN_SEPARATOR + role,
                        Duration.ofSeconds(TOKEN_TTL_SECONDS));
        return new ClassSeatSyncTokenVO(token, TOKEN_TTL_SECONDS);
    }

    public Optional<TokenPayload> consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = stringRedisTemplate.opsForValue().getAndDelete(tokenKey(token));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TokenPayload(UUID.fromString(parts[0]), UUID.fromString(parts[1]),
                    Integer.valueOf(parts[2])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    public record TokenPayload(UUID sessionId, UUID userId, Integer role) {
    }
}
