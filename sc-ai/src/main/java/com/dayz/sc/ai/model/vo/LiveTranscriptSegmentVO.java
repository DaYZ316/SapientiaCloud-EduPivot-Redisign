package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.UUID;

/**
 * LiveTranscriptSegmentVO.
 *
 * @author DaYZ
 */
public record LiveTranscriptSegmentVO(
        UUID id,
        UUID summarySessionId,
        UUID classSessionId,
        Integer sequenceNo,
        UUID speakerId,
        String text,
        Integer beginTimeMs,
        Integer endTimeMs,
        Instant createdAt
) {
}
