package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.LivePracticeGroupMapper;
import com.dayz.sc.course.model.entity.LivePracticeGroup;
import com.dayz.sc.course.repository.LivePracticeGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 随堂练习分组仓储MyBatis-Plus实现
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Repository
@RequiredArgsConstructor
public class MybatisLivePracticeGroupRepository implements LivePracticeGroupRepository {

    private final LivePracticeGroupMapper livePracticeGroupMapper;

    @Override
    public Optional<LivePracticeGroup> findById(UUID id) {
        return Optional.ofNullable(livePracticeGroupMapper.selectById(id));
    }

    @Override
    public void save(LivePracticeGroup group) {
        livePracticeGroupMapper.insert(group);
    }

    @Override
    public List<LivePracticeGroup> findByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LivePracticeGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeGroup::getClassSessionId, classSessionId);
        wrapper.orderByAsc(LivePracticeGroup::getPublishOrder);
        return livePracticeGroupMapper.selectList(wrapper);
    }

    @Override
    public List<LivePracticeGroup> findByCourseId(UUID courseId) {
        LambdaQueryWrapper<LivePracticeGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeGroup::getCourseId, courseId);
        wrapper.orderByDesc(LivePracticeGroup::getPublishedAt);
        return livePracticeGroupMapper.selectList(wrapper);
    }

    @Override
    public long countByClassSessionId(UUID classSessionId) {
        LambdaQueryWrapper<LivePracticeGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeGroup::getClassSessionId, classSessionId);
        return livePracticeGroupMapper.selectCount(wrapper);
    }
}
