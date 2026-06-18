package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.LivePracticeQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivePracticeQuestionRepository {

    Optional<LivePracticeQuestion> findById(UUID id);

    void saveBatch(List<LivePracticeQuestion> questions);

    List<LivePracticeQuestion> findByGroupId(UUID groupId);

    List<LivePracticeQuestion> findByCourseId(UUID courseId);
}
