package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.QuestionAnswer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface QuestionAnswerRepository {

    /**
     * 根据ID查询题目答案。
     *
     * @param id 答案ID
     * @return 答案实体，可能为空
     */
    Optional<QuestionAnswer> findById(UUID id);

    /**
     * 保存题目答案。
     *
     * @param answer 答案实体
     */
    void save(QuestionAnswer answer);

    /**
     * 批量保存题目答案。
     *
     * @param answers 答案列表
     */
    void saveBatch(List<QuestionAnswer> answers);

    /**
     * 更新题目答案。
     *
     * @param answer 答案实体
     */
    void update(QuestionAnswer answer);

    /**
     * 根据ID删除题目答案。
     *
     * @param id 答案ID
     */
    void deleteById(UUID id);

    /**
     * 根据题目ID删除所有答案。
     *
     * @param questionId 题目ID
     */
    void deleteByQuestionId(UUID questionId);

    /**
     * 根据题目ID查询所有答案。
     *
     * @param questionId 题目ID
     * @return 答案列表
     */
    List<QuestionAnswer> findByQuestionId(UUID questionId);
}
