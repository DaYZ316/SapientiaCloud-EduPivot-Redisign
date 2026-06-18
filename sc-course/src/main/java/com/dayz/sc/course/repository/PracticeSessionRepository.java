package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.PracticeSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public interface PracticeSessionRepository {

    /**
     * 根据ID查询练习会话
     *
     * @param id 练习会话ID
     * @return 练习会话实体，可能为空
     */
    Optional<PracticeSession> findById(UUID id);

    /**
     * 保存练习会话
     *
     * @param session 练习会话实体
     */
    void save(PracticeSession session);

    /**
     * 更新练习会话
     *
     * @param session 练习会话实体
     */
    void update(PracticeSession session);

    /**
     * 根据用户ID查询练习会话
     *
     * @param sysUserId 用户ID
     * @return 练习会话列表
     */
    List<PracticeSession> findBySysUserId(UUID sysUserId);

    /**
     * 根据题库ID查询练习会话
     *
     * @param questionBankId 题库ID
     * @return 练习会话列表
     */
    List<PracticeSession> findByQuestionBankId(UUID questionBankId);

    /**
     * 统计题库的练习会话数量
     *
     * @param questionBankId 题库ID
     * @return 练习会话数量
     */
    long countByQuestionBankId(UUID questionBankId);

    /**
     * 统计用户的练习会话数量
     *
     * @param sysUserId 用户ID
     * @return 练习会话数量
     */
    long countBySysUserId(UUID sysUserId);
}
