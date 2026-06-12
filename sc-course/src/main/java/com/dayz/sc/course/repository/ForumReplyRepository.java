package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.ForumReply;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumReplyRepository {

    Optional<ForumReply> findById(UUID id);

    void save(ForumReply reply);

    void update(ForumReply reply);

    void deleteById(UUID id);

    List<ForumReply> findByPostId(UUID postId, int page, int size);

    List<ForumReply> findByParentReplyId(UUID parentReplyId);

    List<ForumReply> findAll(int page, int size, UUID postId, UUID forumId, UUID courseId, Integer status);

    long countAll(UUID postId, UUID forumId, UUID courseId, Integer status);

    long countByPostId(UUID postId);

    int findMaxFloorNumber(UUID postId);
}
