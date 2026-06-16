package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ForumPostMapper;
import com.dayz.sc.course.model.entity.ForumPost;
import com.dayz.sc.course.repository.ForumPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
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
    public Page<ForumPost> findAll(int page, int size, UUID courseId, Integer status, String keyword) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
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

}
