package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ForumReplyMapper;
import com.dayz.sc.course.model.entity.ForumReply;
import com.dayz.sc.course.repository.ForumReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisForumReplyRepository implements ForumReplyRepository {

    private final ForumReplyMapper forumReplyMapper;

    @Override
    public Optional<ForumReply> findById(UUID id) {
        return Optional.ofNullable(forumReplyMapper.selectById(id));
    }

    @Override
    public void save(ForumReply reply) {
        forumReplyMapper.insert(reply);
    }

    @Override
    public void update(ForumReply reply) {
        forumReplyMapper.updateById(reply);
    }

    @Override
    public void deleteById(UUID id) {
        forumReplyMapper.deleteById(id);
    }

    @Override
    public Page<ForumReply> findByPostId(UUID postId, int page, int size) {
        LambdaQueryWrapper<ForumReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumReply::getPostId, postId);
        wrapper.eq(ForumReply::getStatus, 0);
        wrapper.orderByAsc(ForumReply::getFloorNumber);
        return forumReplyMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<ForumReply> findByParentReplyId(UUID parentReplyId) {
        LambdaQueryWrapper<ForumReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumReply::getParentReplyId, parentReplyId);
        wrapper.eq(ForumReply::getStatus, 0);
        wrapper.orderByAsc(ForumReply::getCreatedAt);
        return forumReplyMapper.selectList(wrapper);
    }

    @Override
    public Page<ForumReply> findAll(int page, int size, UUID postId, UUID forumId, UUID courseId, Integer status) {
        LambdaQueryWrapper<ForumReply> wrapper = new LambdaQueryWrapper<>();
        if (postId != null) {
            wrapper.eq(ForumReply::getPostId, postId);
        }
        if (forumId != null) {
            wrapper.eq(ForumReply::getForumId, forumId);
        }
        if (courseId != null) {
            wrapper.eq(ForumReply::getCourseId, courseId);
        }
        if (status != null) {
            wrapper.eq(ForumReply::getStatus, status);
        }
        wrapper.orderByAsc(ForumReply::getFloorNumber);
        return forumReplyMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public long countByPostId(UUID postId) {
        LambdaQueryWrapper<ForumReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumReply::getPostId, postId);
        return forumReplyMapper.selectCount(wrapper);
    }

    @Override
    public int findMaxFloorNumber(UUID postId) {
        LambdaQueryWrapper<ForumReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForumReply::getPostId, postId);
        wrapper.orderByDesc(ForumReply::getFloorNumber);
        wrapper.last("LIMIT 1");
        ForumReply reply = forumReplyMapper.selectOne(wrapper);
        return reply != null ? reply.getFloorNumber() : 0;
    }
}
