package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveSummarySession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 实时摘要会话仓储
 *
 * @author DaYZ
 * @since 2026-06-27
 */
public interface LiveSummarySessionRepository {

    /**
     * 保存实时摘要会话
     *
     * @param session 会话实体
     */
    void save(LiveSummarySession session);

    /**
     * 更新实时摘要会话
     *
     * @param session 会话实体
     */
    void update(LiveSummarySession session);

    void resume(LiveSummarySession session);

    /**
     * 根据ID查询实时摘要会话
     *
     * @param id 会话ID
     * @return 会话实体，可能为空
     */
    Optional<LiveSummarySession> findById(UUID id);

    /**
     * 查询课堂会话下最新的实时摘要会话
     *
     * @param classSessionId 课堂会话ID
     * @return 会话实体，可能为空
     */
    Optional<LiveSummarySession> findLatestByClassSessionId(UUID classSessionId);

    /**
     * 查询课堂会话下正在运行的实时摘要会话
     *
     * @param classSessionId 课堂会话ID
     * @return 会话实体，可能为空
     */
    Optional<LiveSummarySession> findRunningByClassSessionId(UUID classSessionId);

    List<LiveSummarySession> findWithSnapshotsByCourseIds(List<UUID> courseIds, int page, int size);

    long countWithSnapshotsByCourseIds(List<UUID> courseIds);

    int countByClassSessionId(UUID classSessionId);

    void deleteById(UUID id);

    /**
     * Delete all live summary sessions for a class session.
     *
     * @param classSessionId class session ID
     */
    void deleteByClassSessionId(UUID classSessionId);
}
