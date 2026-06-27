package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.LiveTranscriptSegmentMapper;
import com.dayz.sc.ai.model.entity.LiveTranscriptSegment;
import com.dayz.sc.ai.repository.LiveTranscriptSegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * MybatisLiveTranscriptSegmentRepository.
 *
 * @author DaYZ
 */
@Repository
@RequiredArgsConstructor
public class MybatisLiveTranscriptSegmentRepository implements LiveTranscriptSegmentRepository {

    private final LiveTranscriptSegmentMapper mapper;

    @Override
    public void save(LiveTranscriptSegment segment) {
        mapper.insert(segment);
    }

    @Override
    public int nextSequenceNo(UUID summarySessionId) {
        LambdaQueryWrapper<LiveTranscriptSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveTranscriptSegment::getSummarySessionId, summarySessionId);
        return Math.toIntExact(mapper.selectCount(wrapper)) + 1;
    }

    @Override
    public List<LiveTranscriptSegment> findRecentBySummarySessionId(UUID summarySessionId, int limit) {
        LambdaQueryWrapper<LiveTranscriptSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveTranscriptSegment::getSummarySessionId, summarySessionId);
        wrapper.orderByDesc(LiveTranscriptSegment::getSequenceNo);
        wrapper.last("LIMIT " + limit);
        return mapper.selectList(wrapper).reversed();
    }

    @Override
    public List<LiveTranscriptSegment> findAfterSequence(UUID summarySessionId, int sequenceNo) {
        LambdaQueryWrapper<LiveTranscriptSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveTranscriptSegment::getSummarySessionId, summarySessionId);
        wrapper.gt(LiveTranscriptSegment::getSequenceNo, sequenceNo);
        wrapper.orderByAsc(LiveTranscriptSegment::getSequenceNo);
        return mapper.selectList(wrapper);
    }
}
