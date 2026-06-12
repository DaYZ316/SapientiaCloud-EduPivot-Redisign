package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Question;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository {

    Optional<Question> findById(UUID id);

    void save(Question question);

    void update(Question question);

    void deleteById(UUID id);

    Page<Question> findByQuestionBankId(UUID questionBankId, int page, int size);

    Page<Question> findAll(int page, int size, UUID questionBankId, UUID courseId,
                           Integer questionType, Integer difficulty, Integer status, String keyword);

    long countByQuestionBankId(UUID questionBankId);

    /**
     * 批量统计每个题库下的题目数量。
     *
     * @param bankIds 题库 ID 列表
     * @return 题库 ID → 题目数量的映射
     */
    Map<UUID, Long> countByQuestionBankIds(List<UUID> bankIds);
}
