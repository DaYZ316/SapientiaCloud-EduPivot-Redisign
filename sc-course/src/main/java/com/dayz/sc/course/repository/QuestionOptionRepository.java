package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.QuestionOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionOptionRepository {

    Optional<QuestionOption> findById(UUID id);

    void save(QuestionOption option);

    void saveBatch(List<QuestionOption> options);

    void update(QuestionOption option);

    void deleteById(UUID id);

    void deleteByQuestionId(UUID questionId);

    List<QuestionOption> findByQuestionId(UUID questionId);
}
