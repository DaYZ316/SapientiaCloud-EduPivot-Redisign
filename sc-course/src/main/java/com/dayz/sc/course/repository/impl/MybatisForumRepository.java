package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ForumMapper;
import com.dayz.sc.course.model.entity.Forum;
import com.dayz.sc.course.repository.ForumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisForumRepository implements ForumRepository {

    private final ForumMapper forumMapper;

    @Override
    public Optional<Forum> findById(UUID id) {
        return Optional.ofNullable(forumMapper.selectById(id));
    }

    @Override
    public Optional<Forum> findDefaultByCourseId(UUID courseId) {
        LambdaQueryWrapper<Forum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Forum::getCourseId, courseId);
        wrapper.eq(Forum::getStatus, 0);
        wrapper.orderByAsc(Forum::getCreatedAt);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(forumMapper.selectOne(wrapper));
    }

    @Override
    public void save(Forum forum) {
        forumMapper.insert(forum);
    }

    @Override
    public void update(Forum forum) {
        forumMapper.updateById(forum);
    }

    @Override
    public void deleteById(UUID id) {
        forumMapper.deleteById(id);
    }

    @Override
    public List<Forum> findByCourseId(UUID courseId) {
        LambdaQueryWrapper<Forum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Forum::getCourseId, courseId);
        wrapper.orderByDesc(Forum::getCreatedAt);
        return forumMapper.selectList(wrapper);
    }

    @Override
    public Page<Forum> findAll(int page, int size, UUID courseId, Integer forumType, Integer status) {
        LambdaQueryWrapper<Forum> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Forum::getCourseId, courseId);
        }
        if (forumType != null) {
            wrapper.eq(Forum::getForumType, forumType);
        }
        if (status != null) {
            wrapper.eq(Forum::getStatus, status);
        }
        wrapper.orderByDesc(Forum::getCreatedAt);
        return forumMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public long countByCourseId(UUID courseId) {
        LambdaQueryWrapper<Forum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Forum::getCourseId, courseId);
        return forumMapper.selectCount(wrapper);
    }
}
