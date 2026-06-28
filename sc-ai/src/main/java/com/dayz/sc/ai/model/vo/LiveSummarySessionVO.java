package com.dayz.sc.ai.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LiveSummarySessionVO.
 *
 * @author DaYZ
 */
public record LiveSummarySessionVO(
        @Nullable UUID id,
        UUID classSessionId,
        UUID courseId,
        UUID teacherId,
        String status,
        @Nullable Instant startedAt,
        @Nullable Instant stoppedAt,
        @Nullable LiveSummarySnapshotVO latestSnapshot,
        List<LiveTranscriptSegmentVO> recentTranscripts,
        Integer historyRecordCount,
        Integer historyRecordLimit,
        Boolean historyRecordLimitReached,
        Integer snapshotCount,
        Integer snapshotLimit,
        Boolean snapshotLimitReached
) {
}
