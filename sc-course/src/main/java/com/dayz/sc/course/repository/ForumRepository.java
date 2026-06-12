package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Forum;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumRepository {

    Optional<Forum> findById(UUID id);

    void save(Forum forum);

    void update(Forum forum);

    void deleteById(UUID id);

    List<Forum> findByCourseId(UUID courseId);

    Page<Forum> findAll(int page, int size, UUID courseId, Integer forumType, Integer status);

    long countByCourseId(UUID courseId);
}
