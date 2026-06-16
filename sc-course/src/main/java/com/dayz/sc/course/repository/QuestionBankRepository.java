package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.QuestionBank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface QuestionBankRepository {

    /**
     * 根据ID查询题库。
     *
     * @param id 题库ID
     * @return 题库实体，可能为空
     */
    Optional<QuestionBank> findById(UUID id);

    /**
     * 保存题库。
     *
     * @param bank 题库实体
     */
    void save(QuestionBank bank);

    /**
     * 更新题库。
     *
     * @param bank 题库实体
     */
    void update(QuestionBank bank);

    /**
     * 根据ID删除题库。
     *
     * @param id 题库ID
     */
    void deleteById(UUID id);

    /**
     * 根据课程ID查询题库。
     *
     * @param courseId 课程ID
     * @return 题库列表
     */
    List<QuestionBank> findByCourseId(UUID courseId);

    /**
     * 分页查询题库。
     *
     * @param page     页码
     * @param size     每页大小
     * @param courseId 课程ID
     * @param bankType 题库类型
     * @param keyword  关键词
     * @return 分页结果
     */
    Page<QuestionBank> findAll(int page, int size, UUID courseId, Integer bankType, String keyword);

    /**
     * 统计课程的题库数量。
     *
     * @param courseId 课程ID
     * @return 题库数量
     */
    long countByCourseId(UUID courseId);
}
