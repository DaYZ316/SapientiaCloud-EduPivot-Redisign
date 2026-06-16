package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ClassSession;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for class sessions.
 */
public interface ClassSessionRepository {

    Optional<ClassSession> findById(UUID id);

    Optional<ClassSession> findByIdForUpdate(UUID id);

    void save(ClassSession session);

    void update(ClassSession session);

    void deleteById(UUID id);

    Page<ClassSession> findByCourseId(UUID courseId, int page, int size, boolean includeDrafts);
}
