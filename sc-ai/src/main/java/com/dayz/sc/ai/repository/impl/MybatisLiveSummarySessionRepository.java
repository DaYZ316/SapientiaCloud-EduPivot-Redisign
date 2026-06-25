package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.LiveSummarySessionMapper;
import com.dayz.sc.ai.model.entity.LiveSummarySession;
import com.dayz.sc.ai.model.enums.LiveSummaryStatus;
import com.dayz.sc.ai.repository.LiveSummarySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisLiveSummarySessionRepository implements LiveSummarySessionRepository {

    private final LiveSummarySessionMapper mapper;

    @Override
    public void save(LiveSummarySession session) {
        mapper.insert(session);
    }

    @Override
    public void update(LiveSummarySession session) {
        mapper.updateById(session);
    }

    @Override
    public Optional<LiveSummarySession> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<LiveSummarySession> findLatestByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LiveSummarySession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySession::getClassSessionId, classSessionId);
        wrapper.orderByDesc(LiveSummarySession::getCreatedAt);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public Optional<LiveSummarySession> findRunningByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LiveSummarySession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySession::getClassSessionId, classSessionId);
        wrapper.eq(LiveSummarySession::getStatus, LiveSummaryStatus.RUNNING.name());
        wrapper.orderByDesc(LiveSummarySession::getCreatedAt);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }
}
