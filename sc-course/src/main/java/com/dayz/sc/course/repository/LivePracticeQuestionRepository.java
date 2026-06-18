package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.LivePracticeQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 随堂练习题目快照仓储
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public interface LivePracticeQuestionRepository {

    /**
     * 根据ID查询题目快照
     *
     * @param id 题目快照ID
     * @return 题目快照实体，可能为空
     */
    Optional<LivePracticeQuestion> findById(UUID id);

    /**
     * 批量保存题目快照
     *
     * @param questions 题目快照列表
     */
    void saveBatch(List<LivePracticeQuestion> questions);

    /**
     * 根据分组ID查询题目快照列表
     *
     * @param groupId 分组ID
     * @return 题目快照列表
     */
    List<LivePracticeQuestion> findByGroupId(UUID groupId);

    /**
     * 根据课程ID查询题目快照列表
     *
     * @param courseId 课程ID
     * @return 题目快照列表
     */
    List<LivePracticeQuestion> findByCourseId(UUID courseId);
}
