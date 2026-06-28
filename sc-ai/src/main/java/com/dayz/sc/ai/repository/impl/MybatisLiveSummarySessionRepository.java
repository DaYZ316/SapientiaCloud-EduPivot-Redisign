package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dayz.sc.ai.mapper.LiveSummarySessionMapper;
import com.dayz.sc.ai.model.entity.LiveSummarySession;
import com.dayz.sc.ai.model.enums.LiveSummaryStatus;
import com.dayz.sc.ai.repository.LiveSummarySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MybatisLiveSummarySessionRepository.
 *
 * @author DaYZ
 */
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
    public void resume(LiveSummarySession session) {
        LambdaUpdateWrapper<LiveSummarySession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(LiveSummarySession::getId, session.getId());
        wrapper.set(LiveSummarySession::getStatus, session.getStatus());
        wrapper.set(LiveSummarySession::getStoppedAt, null);
        mapper.update(wrapper);
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

    @Override
    public List<LiveSummarySession> findWithSnapshotsByCourseIds(List<UUID> courseIds, int page, int size) {
        int pageSize = Math.max(1, size);
        int offset = (Math.max(1, page) - 1) * pageSize;
        return mapper.findWithSnapshotsByCourseIds(courseIds, pageSize, offset);
    }

    @Override
    public long countWithSnapshotsByCourseIds(List<UUID> courseIds) {
        return mapper.countWithSnapshotsByCourseIds(courseIds);
    }

    @Override
    public int countByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LiveSummarySession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySession::getClassSessionId, classSessionId);
        return Math.toIntExact(mapper.selectCount(wrapper));
    }

    @Override
    public void deleteById(UUID id) {
        mapper.deleteById(id);
    }

    @Override
    public void deleteByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LiveSummarySession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LiveSummarySession::getClassSessionId, classSessionId);
        mapper.delete(wrapper);
    }
}
