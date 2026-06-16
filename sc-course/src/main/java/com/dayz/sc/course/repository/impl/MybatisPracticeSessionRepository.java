package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.PracticeSessionMapper;
import com.dayz.sc.course.model.entity.PracticeSession;
import com.dayz.sc.course.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口。
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Repository
@RequiredArgsConstructor
public class MybatisPracticeSessionRepository implements PracticeSessionRepository {

    private final PracticeSessionMapper practiceSessionMapper;

    @Override
    public Optional<PracticeSession> findById(UUID id) {
        return Optional.ofNullable(practiceSessionMapper.selectById(id));
    }

    @Override
    public void save(PracticeSession session) {
        practiceSessionMapper.insert(session);
    }

    @Override
    public void update(PracticeSession session) {
        practiceSessionMapper.updateById(session);
    }

    @Override
    public List<PracticeSession> findBySysUserId(UUID sysUserId) {
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getSysUserId, sysUserId);
        wrapper.orderByDesc(PracticeSession::getStartedAt);
        return practiceSessionMapper.selectList(wrapper);
    }

    @Override
    public List<PracticeSession> findByQuestionBankId(UUID questionBankId) {
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getQuestionBankId, questionBankId);
        wrapper.orderByDesc(PracticeSession::getStartedAt);
        return practiceSessionMapper.selectList(wrapper);
    }

    @Override
    public long countByQuestionBankId(UUID questionBankId) {
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getQuestionBankId, questionBankId);
        return practiceSessionMapper.selectCount(wrapper);
    }

    @Override
    public long countBySysUserId(UUID sysUserId) {
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getSysUserId, sysUserId);
        return practiceSessionMapper.selectCount(wrapper);
    }
}
