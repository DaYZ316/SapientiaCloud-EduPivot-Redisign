package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ForumPost;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumPostRepository {

    Optional<ForumPost> findById(UUID id);

    void save(ForumPost post);

    void update(ForumPost post);

    void deleteById(UUID id);

    Page<ForumPost> findByForumId(UUID forumId, int page, int size);

    Page<ForumPost> findByCourseId(UUID courseId, int page, int size);

    Page<ForumPost> findAll(int page, int size, UUID forumId, UUID courseId, Integer status, String keyword);

    long countByForumId(UUID forumId);

    List<ForumPost> findHotPosts(UUID courseId, int limit);

    List<ForumPost> findLatestPosts(UUID courseId, int limit);
}
