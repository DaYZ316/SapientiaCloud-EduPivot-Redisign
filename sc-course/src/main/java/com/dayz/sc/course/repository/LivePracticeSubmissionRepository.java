package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.LivePracticeSubmission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LivePracticeSubmissionRepository {

    Optional<LivePracticeSubmission> findByGroupIdAndQuestionSnapshotIdAndStudentId(UUID groupId, UUID questionSnapshotId, UUID studentId);

    void save(LivePracticeSubmission submission);

    List<LivePracticeSubmission> findByGroupId(UUID groupId);

    List<LivePracticeSubmission> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    List<LivePracticeSubmission> findByGroupIds(List<UUID> groupIds);
}
