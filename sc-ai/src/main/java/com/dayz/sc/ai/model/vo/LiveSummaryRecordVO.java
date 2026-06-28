package com.dayz.sc.ai.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Live summary record shown in the AI workspace minutes library.
 *
 * @author DaYZ
 */
public record LiveSummaryRecordVO(
        UUID id,
        UUID classSessionId,
        UUID courseId,
        UUID teacherId,
        String status,
        @Nullable Instant startedAt,
        @Nullable Instant stoppedAt,
        @Nullable LiveSummarySnapshotVO latestSnapshot
) {
}
