package com.dayz.sc.common.events.ai;

import com.dayz.sc.common.util.UuidV7Generator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * QuestionGenerationCompletedEvent.
 *
 * @author DaYZ
 */
public record QuestionGenerationCompletedEvent(
        UUID eventId,
        String requestId,
        String status,
        String content,
        String messageType,
        Map<String, Object> payload,
        String errorMessage,
        Instant timestamp
) {
    public static QuestionGenerationCompletedEvent completed(String requestId,
                                                             String content,
                                                             String messageType,
                                                             Map<String, Object> payload) {
        return new QuestionGenerationCompletedEvent(
                UuidV7Generator.generate(),
                requestId,
                "completed",
                content,
                messageType,
                payload,
                null,
                Instant.now());
    }

    public static QuestionGenerationCompletedEvent failed(String requestId, String errorMessage) {
        return new QuestionGenerationCompletedEvent(
                UuidV7Generator.generate(),
                requestId,
                "error",
                "",
                null,
                Map.of(),
                errorMessage,
                Instant.now());
    }
}
