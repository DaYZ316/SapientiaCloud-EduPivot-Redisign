package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveSummarySession;

import java.util.Optional;
import java.util.UUID;

public interface LiveSummarySessionRepository {

    void save(LiveSummarySession session);

    void update(LiveSummarySession session);

    Optional<LiveSummarySession> findById(UUID id);

    Optional<LiveSummarySession> findLatestByClassSessionId(UUID classSessionId);

    Optional<LiveSummarySession> findRunningByClassSessionId(UUID classSessionId);
}
