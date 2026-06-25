package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.ClassParticipantMapper;
import com.dayz.sc.course.model.entity.ClassParticipant;
import com.dayz.sc.course.repository.ClassParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 课堂参与者 MyBatis Repository 实现
 *
 * @author DaYZ
 * @since 2026-06-14
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
    public List<ClassParticipant> findBySessionId(UUID sessionId) {
        LambdaQueryWrapper<ClassParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassParticipant::getSessionId, sessionId);
        wrapper.orderByAsc(ClassParticipant::getSeatIndex);
        return classParticipantMapper.selectList(wrapper);
    }

    @Override
    public List<ClassParticipant> findBySessionIdAndUserIds(UUID sessionId, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ClassParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassParticipant::getSessionId, sessionId);
        wrapper.in(ClassParticipant::getUserId, userIds);
        return classParticipantMapper.selectList(wrapper);
    }

    @Override
    public Optional<ClassParticipant> findBySessionIdAndSeatIndex(UUID sessionId, Integer seatIndex) {
        LambdaQueryWrapper<ClassParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassParticipant::getSessionId, sessionId);
        wrapper.eq(ClassParticipant::getSeatIndex, seatIndex);
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

    @Override
    public void deleteBySessionIdAndUserId(UUID sessionId, UUID userId) {
        LambdaQueryWrapper<ClassParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassParticipant::getSessionId, sessionId);
        wrapper.eq(ClassParticipant::getUserId, userId);
        classParticipantMapper.delete(wrapper);
    }
}
