package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.PracticeAnswer;

import java.util.List;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public interface PracticeAnswerRepository {

    /**
     * 保存练习答案
     *
     * @param answer 答案实体
     */
    void save(PracticeAnswer answer);

    /**
     * 根据练习会话ID查询答案
     *
     * @param sessionId 练习会话ID
     * @return 答案列表
     */
    List<PracticeAnswer> findBySessionId(UUID sessionId);

    /**
     * 根据题目ID查询答案
     *
     * @param questionId 题目ID
     * @return 答案列表
     */
    List<PracticeAnswer> findByQuestionId(UUID questionId);
}
