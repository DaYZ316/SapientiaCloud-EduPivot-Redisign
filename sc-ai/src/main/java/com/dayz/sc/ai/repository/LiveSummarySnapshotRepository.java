package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 实时摘要快照仓储
 *
 * @author DaYZ
 * @since 2026-06-27
 */
public interface LiveSummarySnapshotRepository {

    /**
     * 保存实时摘要快照
     *
     * @param snapshot 快照实体
     */
    void save(LiveSummarySnapshot snapshot);

    /**
     * 获取下一个序列号
     *
     * @param summarySessionId 摘要会话ID
     * @return 下一个序列号
     */
    int nextSequenceNo(UUID summarySessionId);

    /**
     * 查询摘要会话下最新的快照
     *
     * @param summarySessionId 摘要会话ID
     * @return 快照实体，可能为空
     */
    Optional<LiveSummarySnapshot> findLatestBySummarySessionId(UUID summarySessionId);

    Optional<LiveSummarySnapshot> findById(UUID id);

    List<LiveSummarySnapshot> findRecentBySummarySessionId(UUID summarySessionId, int limit);

    List<LiveSummarySnapshot> findRecentByClassSessionId(UUID classSessionId, int limit);

    List<LiveSummarySnapshot> findRecentByCourseId(UUID courseId, int limit);

    void deleteById(UUID id);

    void deleteBySummarySessionId(UUID summarySessionId);

    /**
     * Delete all live summary snapshots for a class session.
     *
     * @param classSessionId class session ID
     */
    void deleteByClassSessionId(UUID classSessionId);
}
