package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * LiveSummarySnapshotVO.
 *
 * @author DaYZ
 */
public record LiveSummarySnapshotVO(
        UUID id,
        UUID summarySessionId,
        UUID classSessionId,
        Integer sequenceNo,
        Integer transcriptUntilSequenceNo,
        String overview,
        Map<String, Object> payload,
        Instant createdAt
) {
}
