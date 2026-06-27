package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.LiveTranscriptSegment;

import java.util.List;
import java.util.UUID;

/**
 * 实时转录片段仓储
 *
 * @author DaYZ
 * @since 2026-06-27
 */
public interface LiveTranscriptSegmentRepository {

    /**
     * 保存转录片段
     *
     * @param segment 转录片段实体
     */
    void save(LiveTranscriptSegment segment);

    /**
     * 获取下一个序列号
     *
     * @param summarySessionId 摘要会话ID
     * @return 下一个序列号
     */
    int nextSequenceNo(UUID summarySessionId);

    /**
     * 查询摘要会话下最近的转录片段
     *
     * @param summarySessionId 摘要会话ID
     * @param limit            查询条数
     * @return 转录片段列表
     */
    List<LiveTranscriptSegment> findRecentBySummarySessionId(UUID summarySessionId, int limit);

    /**
     * 查询指定序列号之后的转录片段
     *
     * @param summarySessionId 摘要会话ID
     * @param sequenceNo       起始序列号
     * @return 转录片段列表
     */
    List<LiveTranscriptSegment> findAfterSequence(UUID summarySessionId, int sequenceNo);
}
