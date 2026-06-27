package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.LiveSummarySnapshotMapper;
import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;
import com.dayz.sc.ai.repository.LiveSummarySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getSummarySessionId, summarySessionId);
        return Math.toIntExact(mapper.selectCount(wrapper)) + 1;
    }

    @Override
    public Optional<LiveSummarySnapshot> findLatestBySummarySessionId(UUID summarySessionId) {
        LambdaQueryWrapper<LiveSummarySnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySnapshot::getSummarySessionId, summarySessionId);
        wrapper.orderByDesc(LiveSummarySnapshot::getSequenceNo);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }
}
