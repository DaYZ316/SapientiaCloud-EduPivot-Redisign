package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.LivePracticeSubmission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 随堂练习提交记录仓储
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public interface LivePracticeSubmissionRepository {

    /**
     * 根据提交记录ID查询提交记录。
     *
     * @param id 提交记录ID
     * @return 提交记录，可能为空
     */
    Optional<LivePracticeSubmission> findById(UUID id);

    /**
     * 根据分组ID、题目快照ID和学生ID查询提交记录
     *
     * @param groupId            分组ID
     * @param questionSnapshotId 题目快照ID
     * @param studentId          学生ID
     * @return 提交记录，可能为空
     */
    Optional<LivePracticeSubmission> findByGroupIdAndQuestionSnapshotIdAndStudentId(UUID groupId, UUID questionSnapshotId, UUID studentId);

    /**
     * 保存提交记录
     *
     * @param submission 提交记录实体
     */
    void save(LivePracticeSubmission submission);

    /**
     * 更新提交记录。
     *
     * @param submission 提交记录实体
     */
    void update(LivePracticeSubmission submission);

    /**
     * 根据分组ID查询所有提交记录
     *
     * @param groupId 分组ID
     * @return 提交记录列表
     */
    List<LivePracticeSubmission> findByGroupId(UUID groupId);

    /**
     * 根据课程ID和学生ID查询提交记录
     *
     * @param courseId  课程ID
     * @param studentId 学生ID
     * @return 提交记录列表
     */
    List<LivePracticeSubmission> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    /**
     * 根据多个分组ID批量查询提交记录
     *
     * @param groupIds 分组ID列表
     * @return 提交记录列表
     */
    List<LivePracticeSubmission> findByGroupIds(List<UUID> groupIds);

    /**
     * 查询指定提交时间之前仍等待AI批改的提交记录。
     *
     * @param submittedBefore 提交时间上限
     * @param limit           返回记录上限
     * @return 等待AI批改的提交记录列表
     */
    List<LivePracticeSubmission> findPendingAiGradingSubmittedBefore(Instant submittedBefore, int limit);
}
