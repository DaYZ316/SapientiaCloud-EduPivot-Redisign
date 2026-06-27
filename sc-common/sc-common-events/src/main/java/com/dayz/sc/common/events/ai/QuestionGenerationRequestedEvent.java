package com.dayz.sc.common.events.ai;

import java.util.Map;
import java.util.UUID;

/**
 * QuestionGenerationRequestedEvent.
 *
 * @author DaYZ
 */
public record QuestionGenerationRequestedEvent(
        UUID eventId,
        String requestId,
        UUID conversationId,
        UUID assistantMessageId,
        UUID userId,
        Integer role,
        UUID courseId,
        String message,
        String mode,
        Map<String, Object> generation
) {
}
