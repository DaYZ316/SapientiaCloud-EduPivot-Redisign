package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ForumPostMapper;
import com.dayz.sc.course.model.entity.ForumPost;
import com.dayz.sc.course.repository.ForumPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisForumPostRepository implements ForumPostRepository {

    private final ForumPostMapper forumPostMapper;

    @Override
    public Optional<ForumPost> findById(UUID id) {
        return Optional.ofNullable(forumPostMapper.selectById(id));
    }

    @Override
    public void save(ForumPost post) {
        forumPostMapper.insert(post);
    }

    @Override
    public void update(ForumPost post) {
        forumPostMapper.updateById(post);
    }

    @Override
    public void deleteById(UUID id) {
        forumPostMapper.deleteById(id);
    }

    @Override
    public Page<ForumPost> findByForumId(UUID forumId, int page, int size) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumPost::getForumId, forumId);
        wrapper.eq(ForumPost::getStatus, 0);
        wrapper.orderByDesc(ForumPost::getIsTop);
        wrapper.orderByDesc(ForumPost::getCreatedAt);
        return forumPostMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<ForumPost> findByCourseId(UUID courseId, int page, int size) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumPost::getCourseId, courseId);
        wrapper.eq(ForumPost::getStatus, 0);
        wrapper.orderByDesc(ForumPost::getCreatedAt);
        return forumPostMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<ForumPost> findAll(int page, int size, UUID forumId, UUID courseId, Integer status, String keyword) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        if (forumId != null) {
            wrapper.eq(ForumPost::getForumId, forumId);
        }
        if (courseId != null) {
            wrapper.eq(ForumPost::getCourseId, courseId);
        }
        if (status != null) {
            wrapper.eq(ForumPost::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ForumPost::getTitle, keyword).or().like(ForumPost::getContent, keyword));
        }
        wrapper.orderByDesc(ForumPost::getIsTop);
        wrapper.orderByDesc(ForumPost::getCreatedAt);
        return forumPostMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public long countByForumId(UUID forumId) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumPost::getForumId, forumId);
        return forumPostMapper.selectCount(wrapper);
    }

    @Override
    public List<ForumPost> findHotPosts(UUID courseId, int limit) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumPost::getCourseId, courseId);
        wrapper.eq(ForumPost::getStatus, 0);
        wrapper.orderByDesc(ForumPost::getLikeCount);
        wrapper.last("LIMIT " + limit);
        return forumPostMapper.selectList(wrapper);
    }

    @Override
    public List<ForumPost> findLatestPosts(UUID courseId, int limit) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumPost::getCourseId, courseId);
        wrapper.eq(ForumPost::getStatus, 0);
        wrapper.orderByDesc(ForumPost::getCreatedAt);
        wrapper.last("LIMIT " + limit);
        return forumPostMapper.selectList(wrapper);
    }
}
