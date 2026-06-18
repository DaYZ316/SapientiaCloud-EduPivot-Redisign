package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.LivePracticeGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivePracticeGroupRepository {

    Optional<LivePracticeGroup> findById(UUID id);

    void save(LivePracticeGroup group);

    List<LivePracticeGroup> findByClassSessionId(UUID classSessionId);

    List<LivePracticeGroup> findByCourseId(UUID courseId);

    long countByClassSessionId(UUID classSessionId);
}
