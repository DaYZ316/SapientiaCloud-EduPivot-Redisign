package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveTranscriptSegment;

import java.util.List;
import java.util.UUID;

public interface LiveTranscriptSegmentRepository {

    void save(LiveTranscriptSegment segment);

    int nextSequenceNo(UUID summarySessionId);

    List<LiveTranscriptSegment> findRecentBySummarySessionId(UUID summarySessionId, int limit);

    List<LiveTranscriptSegment> findAfterSequence(UUID summarySessionId, int sequenceNo);
}
