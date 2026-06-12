package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.Question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository {

    Optional<Question> findById(UUID id);

    void save(Question question);

    void update(Question question);

    void deleteById(UUID id);

    List<Question> findByQuestionBankId(UUID questionBankId, int page, int size);

    List<Question> findAll(int page, int size, UUID questionBankId, UUID courseId,
                           Integer questionType, Integer difficulty, Integer status, String keyword);

    long countAll(UUID questionBankId, UUID courseId,
                  Integer questionType, Integer difficulty, Integer status, String keyword);

    long countByQuestionBankId(UUID questionBankId);
}
