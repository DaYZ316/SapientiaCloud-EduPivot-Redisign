package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Question;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface QuestionRepository {

    /**
     * 根据ID查询题目
     *
     * @param id 题目ID
     * @return 题目实体，可能为空
     */
    Optional<Question> findById(UUID id);

    /**
     * 保存题目
     *
     * @param question 题目实体
     */
    void save(Question question);

    /**
     * 更新题目
     *
     * @param question 题目实体
     */
    void update(Question question);

    /**
     * 根据ID删除题目
     *
     * @param id 题目ID
     */
    void deleteById(UUID id);

    /**
     * 根据题库ID分页查询题目
     *
     * @param questionBankId 题库ID
     * @param page           页码
     * @param size           每页大小
     * @return 分页结果
     */
    Page<Question> findByQuestionBankId(UUID questionBankId, int page, int size);

    /**
     * 分页查询题目
     *
     * @param page           页码
     * @param size           每页大小
     * @param questionBankId 题库ID
     * @param courseId       课程ID
     * @param questionType   题目类型
     * @param difficulty     难度
     * @param status         状态
     * @param keyword        关键词
     * @param sysUserId      创建者ID
     * @return 分页结果
     */
    Page<Question> findAll(int page, int size, UUID questionBankId, UUID courseId,
                           Integer questionType, Integer difficulty, Integer status, String keyword,
                           UUID sysUserId);

    /**
     * 统计题库的题目数量
     *
     * @param questionBankId 题库ID
     * @return 题目数量
     */
    long countByQuestionBankId(UUID questionBankId);

    /**
     * 批量统计题库的题目数量
     *
     * @param bankIds 题库ID列表
     * @return 题库ID与题目数量的映射
     */
    Map<UUID, Long> countByQuestionBankIds(List<UUID> bankIds);

    /**
     * 统计题库的总分
     *
     * @param questionBankId 题库ID
     * @return 总分
     */
    BigDecimal sumScoreByQuestionBankId(UUID questionBankId);
}
