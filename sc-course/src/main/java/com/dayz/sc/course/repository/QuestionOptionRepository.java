package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.QuestionOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface QuestionOptionRepository {

    /**
     * 根据ID查询题目选项。
     *
     * @param id 选项ID
     * @return 选项实体，可能为空
     */
    Optional<QuestionOption> findById(UUID id);

    /**
     * 保存题目选项。
     *
     * @param option 选项实体
     */
    void save(QuestionOption option);

    /**
     * 批量保存题目选项。
     *
     * @param options 选项列表
     */
    void saveBatch(List<QuestionOption> options);

    /**
     * 更新题目选项。
     *
     * @param option 选项实体
     */
    void update(QuestionOption option);

    /**
     * 根据ID删除题目选项。
     *
     * @param id 选项ID
     */
    void deleteById(UUID id);

    /**
     * 根据题目ID删除所有选项。
     *
     * @param questionId 题目ID
     */
    void deleteByQuestionId(UUID questionId);

    /**
     * 根据题目ID查询所有选项。
     *
     * @param questionId 题目ID
     * @return 选项列表
     */
    List<QuestionOption> findByQuestionId(UUID questionId);
}
