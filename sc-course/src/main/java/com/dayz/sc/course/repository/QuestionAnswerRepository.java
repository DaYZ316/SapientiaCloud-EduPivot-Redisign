package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.QuestionAnswer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionAnswerRepository {

    Optional<QuestionAnswer> findById(UUID id);

    void save(QuestionAnswer answer);

    void saveBatch(List<QuestionAnswer> answers);

    void update(QuestionAnswer answer);

    void deleteById(UUID id);

    void deleteByQuestionId(UUID questionId);

    List<QuestionAnswer> findByQuestionId(UUID questionId);
}
