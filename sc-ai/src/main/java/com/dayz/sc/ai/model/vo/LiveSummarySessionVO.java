package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LiveSummarySessionVO(
        UUID id,
        UUID classSessionId,
        UUID courseId,
        UUID teacherId,
        String status,
        Instant startedAt,
        Instant stoppedAt,
        LiveSummarySnapshotVO latestSnapshot,
        List<LiveTranscriptSegmentVO> recentTranscripts
) {
}
