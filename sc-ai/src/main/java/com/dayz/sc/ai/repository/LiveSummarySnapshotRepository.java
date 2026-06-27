package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;

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
}
