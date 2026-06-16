package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ClassSessionMapper;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.repository.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * MyBatis repository for class sessions.
 */
@Repository
@RequiredArgsConstructor
public class MybatisClassSessionRepository implements ClassSessionRepository {

    private final ClassSessionMapper classSessionMapper;

    @Override
    public Optional<ClassSession> findById(UUID id) {
        return Optional.ofNullable(classSessionMapper.selectById(id));
    }

    @Override
    public Optional<ClassSession> findByIdForUpdate(UUID id) {
        QueryWrapper<ClassSession> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).last("FOR UPDATE");
        return Optional.ofNullable(classSessionMapper.selectOne(wrapper));
    }

    @Override
    public void save(ClassSession session) {
        classSessionMapper.insert(session);
    }

    @Override
    public void update(ClassSession session) {
        classSessionMapper.updateById(session);
    }

    @Override
    public void deleteById(UUID id) {
        classSessionMapper.deleteById(id);
    }

    @Override
    public Page<ClassSession> findByCourseId(UUID courseId, int page, int size, boolean includeDrafts) {
        LambdaQueryWrapper<ClassSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassSession::getCourseId, courseId);
        if (!includeDrafts) {
            wrapper.isNotNull(ClassSession::getPublishedAt);
        }
        wrapper.orderByDesc(ClassSession::getScheduledStartAt);
        return classSessionMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
