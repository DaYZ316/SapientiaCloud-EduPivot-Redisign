package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.LiveSummarySnapshotMapper;
import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;
import com.dayz.sc.ai.repository.LiveSummarySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MybatisLiveSummarySnapshotRepository.
 *
 * @author DaYZ
 */
@Repository
@RequiredArgsConstructor
public class MybatisLiveSummarySnapshotRepository implements LiveSummarySnapshotRepository {

    private final LiveSummarySnapshotMapper mapper;

    @Override
    public void save(LiveSummarySnapshot snapshot) {
        mapper.insert(snapshot);
    }

    @Override
    public int nextSequenceNo(UUID summarySessionId) {
        return mapper.maxSequenceNoIncludingDeleted(summarySessionId) + 1;
    }

    @Override
    public Optional<LiveSummarySnapshot> findLatestBySummarySessionId(UUID summarySessionId) {
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getSummarySessionId, summarySessionId);
        wrapper.orderByDesc(LiveSummarySnapshot::getSequenceNo);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public Optional<LiveSummarySnapshot> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<LiveSummarySnapshot> findRecentBySummarySessionId(UUID summarySessionId, int limit) {
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getSummarySessionId, summarySessionId);
        wrapper.orderByDesc(LiveSummarySnapshot::getSequenceNo);
        wrapper.last("LIMIT " + Math.max(1, limit));
        return mapper.selectList(wrapper);
    }

    @Override
    public List<LiveSummarySnapshot> findRecentByClassSessionId(UUID classSessionId, int limit) {
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getClassSessionId, classSessionId);
        wrapper.orderByDesc(LiveSummarySnapshot::getCreatedAt);
        wrapper.last("LIMIT " + Math.max(1, limit));
        return mapper.selectList(wrapper);
    }

    @Override
    public List<LiveSummarySnapshot> findRecentByCourseId(UUID courseId, int limit) {
        return mapper.findRecentActiveByCourseId(courseId, Math.max(1, limit));
    }

    @Override
    public void deleteById(UUID id) {
        mapper.softDeleteById(id);
    }

    @Override
    public void deleteBySummarySessionId(UUID summarySessionId) {
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getSummarySessionId, summarySessionId);
        mapper.delete(wrapper);
    }

    @Override
    public void deleteByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getClassSessionId, classSessionId);
        mapper.delete(wrapper);
    }
}
