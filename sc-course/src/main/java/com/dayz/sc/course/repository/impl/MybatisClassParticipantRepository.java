package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.ClassParticipantMapper;
import com.dayz.sc.course.model.entity.ClassParticipant;
import com.dayz.sc.course.repository.ClassParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * MyBatis repository for class participants.
 */
@Repository
@RequiredArgsConstructor
public class MybatisClassParticipantRepository implements ClassParticipantRepository {

    private final ClassParticipantMapper classParticipantMapper;

    @Override
    public Optional<ClassParticipant> findBySessionIdAndUserId(UUID sessionId, UUID userId) {
        LambdaQueryWrapper<ClassParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassParticipant::getSessionId, sessionId);
        wrapper.eq(ClassParticipant::getUserId, userId);
        return Optional.ofNullable(classParticipantMapper.selectOne(wrapper));
    }

    @Override
    public boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId) {
        LambdaQueryWrapper<ClassParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassParticipant::getSessionId, sessionId);
        wrapper.eq(ClassParticipant::getUserId, userId);
        return classParticipantMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void save(ClassParticipant participant) {
        classParticipantMapper.insert(participant);
    }

    @Override
    public void update(ClassParticipant participant) {
        classParticipantMapper.updateById(participant);
    }
}
