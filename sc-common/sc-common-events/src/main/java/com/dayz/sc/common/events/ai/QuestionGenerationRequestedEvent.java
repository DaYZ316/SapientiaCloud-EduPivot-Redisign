package com.dayz.sc.common.events.ai;

import java.util.Map;
import java.util.UUID;

public record QuestionGenerationRequestedEvent(
        UUID eventId,
        String requestId,
        UUID conversationId,
        UUID userId,
        Integer role,
        UUID courseId,
        String message,
        String mode,
        Map<String, Object> generation
) {
}
