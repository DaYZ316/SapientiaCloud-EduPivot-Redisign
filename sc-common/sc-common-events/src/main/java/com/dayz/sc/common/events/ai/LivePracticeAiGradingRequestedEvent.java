package com.dayz.sc.common.events.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LivePracticeAiGradingRequestedEvent.
 *
 * @author DaYZ
 */
public record LivePracticeAiGradingRequestedEvent(
        UUID eventId,
        UUID submissionId,
        UUID groupId,
        UUID questionSnapshotId,
        UUID courseId,
        UUID classSessionId,
        UUID studentId,
        String questionTitle,
        String questionContent,
        BigDecimal score,
        List<AnswerReference> answers,
        String textAnswer,
        String gradingRequirement,
        String eventType,
        Instant timestamp,
        String source
) {
    public record AnswerReference(
            String answerContent,
            String explanation,
            BigDecimal score,
            Integer sortOrder
    ) {
    }
}
