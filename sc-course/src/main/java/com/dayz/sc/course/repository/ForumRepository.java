package com.dayz.sc.course.repository;

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

    List<Forum> findAll(int page, int size, UUID courseId, Integer forumType, Integer status);

    long countAll(UUID courseId, Integer forumType, Integer status);

    long countByCourseId(UUID courseId);
}
