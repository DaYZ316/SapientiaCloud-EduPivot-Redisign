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

    /**
     * Query a live summary snapshot by ID.
     *
     * @param id snapshot ID
     * @return snapshot entity, possibly empty
     */
    Optional<LiveSummarySnapshot> findById(UUID id);

    /**
     * Query recent live summary snapshots under a summary session.
     *
     * @param summarySessionId summary session ID
     * @param limit            result size limit
     * @return recent snapshot list
     */
    List<LiveSummarySnapshot> findRecentBySummarySessionId(UUID summarySessionId, int limit);

    /**
     * Query recent live summary snapshots under a class session.
     *
     * @param classSessionId class session ID
     * @param limit          result size limit
     * @return recent snapshot list
     */
    List<LiveSummarySnapshot> findRecentByClassSessionId(UUID classSessionId, int limit);

    /**
     * Query recent live summary snapshots under a course.
     *
     * @param courseId course ID
     * @param limit    result size limit
     * @return recent snapshot list
     */
    List<LiveSummarySnapshot> findRecentByCourseId(UUID courseId, int limit);

    /**
     * Delete a live summary snapshot by ID.
     *
     * @param id snapshot ID
     */
    void deleteById(UUID id);

    /**
     * Delete all live summary snapshots under a summary session.
     *
     * @param summarySessionId summary session ID
     */
    void deleteBySummarySessionId(UUID summarySessionId);

    /**
     * Delete all live summary snapshots for a class session.
     *
     * @param classSessionId class session ID
     */
    void deleteByClassSessionId(UUID classSessionId);
}
