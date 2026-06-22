package com.dayz.sc.common.events.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record QuestionGenerationProgressEvent(
        UUID eventId,
        String requestId,
        String eventType,
        Map<String, Object> payload,
        Instant timestamp
) {
}
