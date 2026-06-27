package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.vo.LiveSummaryAudioTokenVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * LiveSummaryAudioTokenService.
 *
 * @author DaYZ
 */
@Service
public class LiveSummaryAudioTokenService {

    private static final long TOKEN_TTL_SECONDS = 60;
    private static final String TOKEN_KEY_PREFIX = "ai-live-summary-audio:";
    private static final String TOKEN_SEPARATOR = "|";
    private static final int TOKEN_PAYLOAD_PARTS = 4;

    private final StringRedisTemplate stringRedisTemplate;

    public LiveSummaryAudioTokenService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public LiveSummaryAudioTokenVO issueToken(UUID classSessionId, UUID summarySessionId, UUID userId, Integer role) {
        String token = UUID.randomUUID().toString();
        String payload = classSessionId + TOKEN_SEPARATOR
                + summarySessionId + TOKEN_SEPARATOR
                + userId + TOKEN_SEPARATOR
                + role;
        stringRedisTemplate.opsForValue()
                .set(tokenKey(token), payload, Duration.ofSeconds(TOKEN_TTL_SECONDS));
        return new LiveSummaryAudioTokenVO(token, TOKEN_TTL_SECONDS);
    }

    public Optional<TokenPayload> consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = stringRedisTemplate.opsForValue().getAndDelete(tokenKey(token));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", TOKEN_PAYLOAD_PARTS);
        if (parts.length != TOKEN_PAYLOAD_PARTS) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TokenPayload(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    UUID.fromString(parts[2]),
                    Integer.valueOf(parts[3])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    public record TokenPayload(UUID classSessionId, UUID summarySessionId, UUID userId, Integer role) {
    }
}
