package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.QuestionBank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionBankRepository {

    Optional<QuestionBank> findById(UUID id);

    void save(QuestionBank bank);

    void update(QuestionBank bank);

    void deleteById(UUID id);

    List<QuestionBank> findByCourseId(UUID courseId);

    Page<QuestionBank> findAll(int page, int size, UUID courseId, Integer bankType, String keyword);

    long countByCourseId(UUID courseId);
}
