package com.dayz.sc.common.events.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * LivePracticeAiGradingCompletedEvent.
 *
 * @author DaYZ
 */
public record LivePracticeAiGradingCompletedEvent(
        UUID eventId,
        UUID submissionId,
        UUID groupId,
        UUID questionSnapshotId,
        UUID courseId,
        UUID classSessionId,
        UUID studentId,
        String status,
        Integer isCorrect,
        BigDecimal earnedScore,
        String feedback,
        String errorMessage,
        String eventType,
        Instant timestamp,
        String source
) {
}
