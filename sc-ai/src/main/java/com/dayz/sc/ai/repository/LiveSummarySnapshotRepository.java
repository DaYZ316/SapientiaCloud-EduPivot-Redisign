package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;

import java.util.Optional;
import java.util.UUID;

public interface LiveSummarySnapshotRepository {

    void save(LiveSummarySnapshot snapshot);

    int nextSequenceNo(UUID summarySessionId);

    Optional<LiveSummarySnapshot> findLatestBySummarySessionId(UUID summarySessionId);
}
