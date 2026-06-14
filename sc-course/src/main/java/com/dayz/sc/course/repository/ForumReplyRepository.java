package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ForumReply;

import java.util.Optional;
import java.util.UUID;

public interface ForumReplyRepository {

    Optional<ForumReply> findById(UUID id);

    void save(ForumReply reply);

    void update(ForumReply reply);

    void deleteById(UUID id);

    Page<ForumReply> findByPostId(UUID postId, int page, int size);

    int findMaxFloorNumber(UUID postId);
}
