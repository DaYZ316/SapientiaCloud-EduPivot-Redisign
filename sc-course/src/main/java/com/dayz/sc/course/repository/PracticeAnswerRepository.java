package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.PracticeAnswer;

import java.util.List;
import java.util.UUID;

public interface PracticeAnswerRepository {

    void save(PracticeAnswer answer);

    List<PracticeAnswer> findBySessionId(UUID sessionId);

    List<PracticeAnswer> findByQuestionId(UUID questionId);
}
